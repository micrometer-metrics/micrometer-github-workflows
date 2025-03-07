/*
 * Copyright 2025 Broadcom.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.release.train;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.release.common.Dependency;
import io.micrometer.release.common.GradleParser;
import io.micrometer.release.common.Input;
import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class DependencyVerifier {

    private final Logger log = LoggerFactory.getLogger(DependencyVerifier.class);

    private final ProcessRunner processRunner;

    private final ObjectMapper objectMapper;

    private final int initialWait;

    private final int timeout;

    private final int waitBetweenRuns;

    private final TimeUnit timeUnit;

    private final Git git;

    DependencyVerifier(ProcessRunner processRunner, ObjectMapper objectMapper) {
        this.processRunner = processRunner;
        this.git = new Git(processRunner);
        this.objectMapper = objectMapper;
        this.timeUnit = TimeUnit.SECONDS;
        this.initialWait = 15;
        this.timeout = 60 * 10;
        this.waitBetweenRuns = 30;
    }

    // for tests
    DependencyVerifier(ProcessRunner processRunner, ObjectMapper objectMapper, Git git, int initialWait, int timeout,
            int waitBetweenRuns, TimeUnit timeUnit) {
        this.processRunner = processRunner;
        this.objectMapper = objectMapper;
        this.git = git;
        this.initialWait = initialWait;
        this.timeout = timeout;
        this.waitBetweenRuns = waitBetweenRuns;
        this.timeUnit = timeUnit;
    }

    void verifyDependencies(String branch, String orgRepository, ProjectSetup projectSetup) {
        File clonedRepo = git.cloneRepo(branch, orgRepository);
        GradleParser gradleParser = getGradleParser(clonedRepo);
        log.info("Fetching all dependencies before dependabot...");
        Set<Dependency> dependenciesBeforeDependabot = micrometerOnly(gradleParser.fetchAllDependencies());
        log.info("Micrometer dependencies before running dependabot {}", dependenciesBeforeDependabot);
        dependabotUpdateStatus(clonedRepo, orgRepository);
        pullTheLatestRepoChanges(clonedRepo);
        Set<Dependency> dependenciesAfterDependabot = micrometerOnly(gradleParser.fetchAllDependencies());
        log.info("Micrometer dependencies after running dependabot {}", dependenciesBeforeDependabot);
        printDiff(dependenciesAfterDependabot, dependenciesBeforeDependabot);
        assertDependencyDiff(dependenciesAfterDependabot, projectSetup);
    }

    private void printDiff(Set<Dependency> dependenciesAfterDependabot, Set<Dependency> dependenciesBeforeDependabot) {
        Set<Dependency> diff = new HashSet<>(dependenciesAfterDependabot);
        diff.removeAll(dependenciesBeforeDependabot);
        log.info("Dependency diff after running dependabot {}", diff);
    }

    private Set<Dependency> micrometerOnly(Set<Dependency> dependencies) {
        return dependencies.stream()
            .filter(dependency -> dependency.group().equalsIgnoreCase("io.micrometer"))
            .collect(Collectors.toSet());
    }

    private void assertDependencyDiff(Set<Dependency> diff, ProjectSetup projectSetup) {
        Set<Dependency> dependencies = projectSetup.expectedDependencies();
        log.info("Expected dependencies from the project setup {}", dependencies);
        Set<Dependency> diffBetweenExpectedAndActual = new HashSet<>(dependencies);
        diffBetweenExpectedAndActual.removeAll(diff);
        if (!diffBetweenExpectedAndActual.isEmpty()) {
            throw new IllegalStateException(
                    "There's a difference between expected dependencies from project setup and the one after running dependabot ["
                            + diffBetweenExpectedAndActual + "]");
        }
        log.info(
                "Project after running dependabot has all project dependencies in required versions! Proceeding with the release...");
    }

    private void dependabotUpdateStatus(File clonedRepo, String orgRepository) {
        String githubServerTime = getGitHubServerTime(orgRepository);
        triggerDependabotCheck(orgRepository, clonedRepo);
        waitForDependabotJobsToFinish(orgRepository, githubServerTime);
        waitForDependabotPrsToFinish(githubServerTime);
    }

    private GradleParser getGradleParser(File branch) {
        ProcessRunner branchProcessRunner = new ProcessRunner(processRunner, branch);
        return gradleParser(branchProcessRunner);
    }

    GradleParser gradleParser(ProcessRunner branchProcessRunner) {
        return new GradleParser(branchProcessRunner);
    }

    private void pullTheLatestRepoChanges(File clonedRepo) {
        log.info("Pulling the latest repo changes");
        processRunnerForBranch(clonedRepo).run("git", "pull");
    }

    private void sleep(int timeoutToSleep) {
        if (timeoutToSleep <= 0) {
            log.warn("Timeout set to {} {}, won't wait, will continue...", timeoutToSleep, timeUnit);
            return;
        }
        try {
            Thread.sleep(timeUnit.toMillis(timeoutToSleep));
        }
        catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private String getGitHubServerTime(String orgRepository) {
        log.info("Retrieving the GH server time...");
        String id = getDependabotupdatesWorkflowId(orgRepository);
        List<String> latestJobDates = processRunner.run("gh", "run", "list", "--workflow=" + id, "-R", orgRepository,
                "--json=createdAt", "--jq=.[].createdAt", "--limit=1");
        if (latestJobDates.isEmpty()) {
            throw new IllegalStateException("Can't get Github server time because no dependabot jobs were ever ran");
        }
        String date = latestJobDates.get(0);
        log.info("GH server time: {}", date);
        return date;
    }

    private void triggerDependabotCheck(String orgRepository, File clonedRepo) {
        log.info("Will trigger a Dependabot check...");
        try {
            String filePath = ".github/dependabot.yml";
            Path path = new File(clonedRepo, filePath).toPath();
            String fileContent = Files.readString(path);
            String triggerComment = "# Triggering dependabot";
            boolean hasComment = fileContent.trim().endsWith(triggerComment);
            if (hasComment) {
                fileContent = fileContent.substring(0, fileContent.lastIndexOf(triggerComment)).trim() + "\n";
                log.info("Removed trigger comment from dependabot.yml");
            }
            else {
                fileContent = fileContent.trim() + "\n" + triggerComment + "\n";
                log.info("Added trigger comment to dependabot.yml");
            }
            Files.writeString(path, fileContent);
            String githubToken = ghToken();
            ProcessRunner branchProcessRunner = processRunnerForBranch(clonedRepo);
            branchProcessRunner.run("git", "remote", "set-url", "origin",
                    "https://x-access-token:" + githubToken + "@github.com/" + orgRepository + ".git");
            branchProcessRunner.run("git", "config", "user.name", "GitHub Action");
            branchProcessRunner.run("git", "config", "user.email", "action@github.com");
            branchProcessRunner.run("git", "pull");
            branchProcessRunner.run("git", "add", filePath);
            branchProcessRunner.run("git", "commit", "-m",
                    "ci: " + (hasComment ? "Remove" : "Add") + " dependabot trigger comment");
            branchProcessRunner.run("git", "push");
        }
        catch (Exception e) {
            log.error("Failed to modify dependabot.yml", e);
            throw new IllegalStateException("Failed to trigger Dependabot check", e);
        }
        log.info("Triggered Dependabot check");
    }

    String ghToken() {
        return Input.getGhToken();
    }

    ProcessRunner processRunnerForBranch(File clonedRepo) {
        return new ProcessRunner(this.processRunner, clonedRepo);
    }

    private void waitForDependabotJobsToFinish(String orgRepository, String githubServerTime) {
        log.info("Waiting {} {} for Dependabot jobs to be created...", initialWait, timeUnit);
        sleep(initialWait);
        log.info("Waiting for Dependabot jobs to finish...");
        String id = getDependabotupdatesWorkflowId(orgRepository);
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeUnit.toMillis(timeout / 2);
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            List<String> curl = curlRuns(orgRepository, githubServerTime, id);
            try {
                Workflows workflows = objectMapper.readValue(String.join("\n", curl), Workflows.class);
                List<Pr> prs = workflows.workflow_runs()
                    .stream()
                    .filter(pr -> "dependabot[bot]".equals(pr.actor().login()))
                    .toList();
                if (prs.isEmpty()) {
                    log.info("No dependabot jobs found");
                }
                else {
                    log.info("Found {} Dependabot jobs", prs.size());
                    boolean allCompleted = prs.stream().allMatch(pr -> pr.status().equalsIgnoreCase("completed"));
                    if (allCompleted) {
                        log.info("All dependabot jobs completed");
                        return;
                    }
                    Set<Pr> notCompleted = prs.stream()
                        .filter(pr -> !pr.status.equalsIgnoreCase("completed"))
                        .collect(Collectors.toSet());
                    log.info("Found {} not completed jobs\n", notCompleted.size());
                    notCompleted.forEach(pr -> log.info("\t{}", pr));
                }
                log.info("Not all Dependabot jobs processed, will try again...");
                sleep(waitBetweenRuns);
            }
            catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        log.error("Failed! Dependabot jobs not processed within the provided timeout");
        throw new IllegalStateException("Timeout waiting for Dependabot jobs to complete");
    }

    List<String> curlRuns(String orgRepository, String githubServerTime, String id) {
        return processRunner.runSilently("curl", "-H", "Authorization: token " + ghToken(),
                "https://api.github.com/repos/" + orgRepository + "/actions/runs?created=>" + githubServerTime
                        + "&workflow_id=" + id);
    }

    record Actor(String login) {

    }

    record Pr(String id, String name, String status, Actor actor) {

    }

    record Workflows(List<Pr> workflow_runs) {

    }

    private String getDependabotupdatesWorkflowId(String orgRepository) {
        List<String> ids = processRunner.run("gh", "workflow", "list", "-R", orgRepository, "--json", "id,name", "--jq",
                ".[] | select(.name==\"Dependabot Updates\") | .id");
        if (ids.isEmpty()) {
            throw new IllegalStateException("Could not find dependabot updates");
        }
        return ids.get(0);
    }

    private void waitForDependabotPrsToFinish(String githubServerTime) {
        log.info("Waiting {} {} for Dependabot PRs to be created...", initialWait, timeUnit);
        sleep(initialWait);
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeUnit.toMillis(timeout);
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            List<String> openPRs = getOpenMicrometerDependabotPRs(githubServerTime);
            if (openPRs.isEmpty()) {
                log.info("No pending Micrometer updates");
                return;
            }
            boolean allProcessed = true;
            for (String pr : openPRs) {
                if (!checkPRStatus(pr)) {
                    allProcessed = false;
                }
            }
            if (allProcessed) {
                log.info("All Dependabot PRs processed");
                return;
            }
            log.info("Not all PRs processed, will try again...");
            sleep(waitBetweenRuns);
        }
        log.error("Failed! PRs not processed within the provided timeout");
        throw new IllegalStateException("Timeout waiting for Dependabot updates");
    }

    private List<String> getOpenMicrometerDependabotPRs(String githubServerTime) {
        log.info("Getting open Micrometer related dependabot PRs...");
        // Example response
        // 5954
        // 5948
        // 5772
        String result = String.join("\n",
                processRunner.run("gh", "pr", "list", "--search",
                        String.format("is:open author:app/dependabot created:>=%s", githubServerTime), "--json",
                        "number,title", "--jq", ".[] | select(.title | contains(\"io.micrometer\")) | .number"));
        List<String> prNumbers = result.lines().filter(line -> !line.trim().isEmpty()).toList();
        log.info("Got [{}] dependabot PRs related to micrometer", prNumbers.size());
        return prNumbers;
    }

    private boolean checkPRStatus(String prNumber) {
        log.info("Will check PR status for PR with number [{}]...", prNumber);
        String status = String.join("\n", processRunner.run("gh", "pr", "view", prNumber, "--json",
                "mergeStateStatus,mergeable,state", "--jq", "[.mergeStateStatus, .state] | join(\",\")"));
        // BLOCKED,OPEN
        // CONFLICTING
        // CLOSED,MERGED
        if (status.contains("CONFLICTING")) {
            log.error("Failed! At least one PR is in CONFLICTING state");
            throw new IllegalStateException("PR #" + prNumber + " has conflicts");
        }
        boolean isCompleted = status.contains("CLOSED") || status.contains("MERGED");
        if (isCompleted) {
            log.info("PR #{} is completed", prNumber);
        }
        else {
            log.info("PR #{} status: {}", prNumber, status);
        }
        return isCompleted;
    }

}
