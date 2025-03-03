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

import io.micrometer.release.common.Dependency;
import io.micrometer.release.common.GradleParser;
import io.micrometer.release.common.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

class DependencyVerifier {

    private final Logger log = LoggerFactory.getLogger(DependencyVerifier.class);

    private final ProcessRunner processRunner;

    private final int initialWait;

    private final int timeout;

    private final int waitBetweenRuns;

    private final TimeUnit timeUnit;

    DependencyVerifier(ProcessRunner processRunner) {
        this.processRunner = processRunner;
        this.timeUnit = TimeUnit.SECONDS;
        this.initialWait = 15;
        this.timeout = 60 * 10;
        this.waitBetweenRuns = 30;
    }

    // for tests
    DependencyVerifier(ProcessRunner processRunner, int initialWait, int timeout, int waitBetweenRuns,
            TimeUnit timeUnit) {
        this.processRunner = processRunner;
        this.initialWait = initialWait;
        this.timeout = timeout;
        this.waitBetweenRuns = waitBetweenRuns;
        this.timeUnit = timeUnit;
    }

    void verifyDependencies(String branch, String orgRepository) {
        File clonedRepo = cloneRepo(branch, orgRepository);
        GradleParser gradleParser = getGradleParser(clonedRepo);
        log.info("Fetching all dependencies before dependabot...");
        Set<Dependency> dependenciesBeforeDependabot = micrometerOnly(gradleParser.fetchAllDependencies());
        Status status = dependabotUpdateStatus(clonedRepo, orgRepository);
        pullTheLatestRepoChanges(clonedRepo);
        Set<Dependency> dependenciesAfterDependabot = micrometerOnly(gradleParser.fetchAllDependencies());
        Set<Dependency> diff = new HashSet<>(dependenciesAfterDependabot);
        diff.removeAll(dependenciesBeforeDependabot);
        log.info("Dependency diff {}", diff);
        assertDependencyDiff(status, diff);
    }

    private Set<Dependency> micrometerOnly(Set<Dependency> dependencies) {
        return dependencies.stream()
            .filter(dependency -> dependency.group().equalsIgnoreCase("io.micrometer"))
            .collect(Collectors.toSet());
    }

    private void assertDependencyDiff(Status status, Set<Dependency> diff) {
        if (status == Status.NO_PRS) {
            log.info("There were no dependabot PRs, the dependency diff should have no differences");
            if (!diff.isEmpty()) {
                log.error("Dependency diff was not empty!");
                throw new IllegalStateException(
                        "There were open PRs however the dependencies differ. Different dependencies: [" + diff + "]");
            }
        }
        else {
            log.info("There were open PRs. The dependency diff should contain new library versions");
            // TODO: Take from env vars micrometer / tracing / context propagation library
            // versions and assert that they were updated in the diff
            // check what this project is (e.g. micrometer) and what it should check for
            // (e.g. context-propagation)
        }
    }

    private Status dependabotUpdateStatus(File clonedRepo, String orgRepository) {
        String githubServerTime = getGitHubServerTime();
        triggerDependabotCheck(orgRepository, clonedRepo);
        waitForDependabotJobsToFinish(orgRepository, githubServerTime);
        return waitForDependabotPrsToFinish(githubServerTime);
    }

    private GradleParser getGradleParser(File branch) {
        ProcessRunner branchProcessRunner = new ProcessRunner(branch);
        return gradleParser(branchProcessRunner);
    }

    GradleParser gradleParser(ProcessRunner branchProcessRunner) {
        return new GradleParser(branchProcessRunner);
    }

    private File cloneRepo(String branch, String orgRepository) {
        log.info("Cloning out {} branch to folder {}", branch, branch);
        processRunner.run("gh", "repo", "clone", orgRepository, branch, "--", "-b", branch, "--single-branch");
        return clonedDir(branch);
    }

    File clonedDir(String branch) {
        return new File(branch);
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

    private String getGitHubServerTime() {
        log.info("Retrieving the GH server time...");
        List<String> response = processRunner.run("gh", "api", "/", "--include");
        String dateHeader = response.stream()
            .filter(line -> line.startsWith("Date:"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Could not get GitHub server time from response headers"));
        // Parse RFC 1123 date to ZonedDateTime and format as ISO-8601 (done by default by
        // dateTime.toInstant())
        ZonedDateTime dateTime = ZonedDateTime.parse(dateHeader.substring(5).trim(), RFC_1123_DATE_TIME);
        String serverTime = dateTime.toInstant().toString();
        log.info("GH server time: {}", serverTime);
        return serverTime;
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
        return System.getenv("GH_TOKEN");
    }

    ProcessRunner processRunnerForBranch(File clonedRepo) {
        return new ProcessRunner(this.processRunner, clonedRepo);
    }

    private void waitForDependabotJobsToFinish(String orgRepository, String githubServerTime) {
        log.info("Waiting {} {} for Dependabot jobs to be created...", initialWait, timeUnit);
        sleep(initialWait);
        log.info("Waiting for Dependabot jobs to finish...");
        List<String> ids = processRunner.run("gh", "workflow", "list", "-R", orgRepository, "--json", "id,name", "--jq",
                ".[] | select(.name==\"Dependabot Updates\") | .id");
        if (ids.isEmpty()) {
            throw new IllegalStateException("Could not find dependabot updates");
        }
        String id = ids.get(0);
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeUnit.toMillis(timeout / 2);
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            List<String> statuses = processRunner.run("gh", "run", "list", "--workflow=" + id, "-R", orgRepository,
                    "--created=\">" + githubServerTime + "\"", "--json", "status", "--jq", ".[].status");
            if (statuses.isEmpty()) {
                log.info("No dependabot jobs found");
            }
            else {
                log.info("Found {} Dependabot jobs", statuses.size());
                boolean allCompleted = statuses.stream().allMatch(s -> s.equalsIgnoreCase("completed"));
                if (allCompleted) {
                    log.info("All dependabot jobs completed");
                    return;
                }
            }
            log.info("Not all Dependabot jobs processed, will try again...");
            sleep(waitBetweenRuns);
        }
        log.error("Failed! Dependabot jobs not processed within the provided timeout");
        throw new IllegalStateException("Timeout waiting for Dependabot jobs to complete");
    }

    private Status waitForDependabotPrsToFinish(String githubServerTime) {
        log.info("Waiting {} {} for Dependabot PRs to be created...", initialWait, timeUnit);
        sleep(initialWait);
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeUnit.toMillis(timeout);
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            List<String> openPRs = getOpenMicrometerDependabotPRs(githubServerTime);
            if (openPRs.isEmpty()) {
                log.info("No pending Micrometer updates");
                return Status.NO_PRS;
            }
            boolean allProcessed = true;
            for (String pr : openPRs) {
                if (!checkPRStatus(pr)) {
                    allProcessed = false;
                }
            }
            if (allProcessed) {
                log.info("All Dependabot PRs processed");
                return Status.ALL_PRS_COMPLETED;
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

    enum Status {

        NO_PRS, ALL_PRS_COMPLETED

    }

}
