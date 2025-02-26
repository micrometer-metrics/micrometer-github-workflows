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
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
        cloneRepo(branch, orgRepository);
        GradleParser gradleParser = getGradleParser(branch);
        log.info("Fetching all dependencies before dependabot...");
        Set<Dependency> dependenciesBeforeDependabot = gradleParser.fetchAllDependencies();
        Status status = dependabotUpdateStatus(orgRepository);
        pullTheLatestRepoChanges();
        Set<Dependency> dependenciesAfterDependabot = gradleParser.fetchAllDependencies();
        Set<Dependency> diff = new HashSet<>(dependenciesAfterDependabot);
        diff.removeAll(dependenciesBeforeDependabot);
        log.info("Dependency diff {}", diff);
        assertDependencyDiff(status, diff);
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

    private Status dependabotUpdateStatus(String orgRepository) {
        String githubServerTime = getGitHubServerTime();
        triggerDependabotCheck(orgRepository);
        log.info("Waiting {} {} for PRs to be created...", initialWait, timeUnit);
        sleep(initialWait);
        return waitForDependabotUpdates(githubServerTime);
    }

    private GradleParser getGradleParser(String branch) {
        ProcessRunner branchProcessRunner = new ProcessRunner(null, new File(branch));
        return gradleParser(branchProcessRunner);
    }

    GradleParser gradleParser(ProcessRunner branchProcessRunner) {
        return new GradleParser(branchProcessRunner);
    }

    private void cloneRepo(String branch, String orgRepository) {
        log.info("Cloning out {} branch to folder {}", branch, branch);
        processRunner.run("git", "clone", "-b", branch, "--single-branch",
                "https://github.com/" + orgRepository + ".git", branch);
    }

    private void pullTheLatestRepoChanges() {
        log.info("Pulling the latest repo changes");
        processRunner.run("git", "pull");
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

    private void triggerDependabotCheck(String orgRepository) {
        log.info("Will trigger a Dependabot check...");
        processRunner.run("gh", "api", "/repos/" + orgRepository + "/dispatches", "-X", "POST", "-F",
                "event_type=check-dependencies");
        log.info("Triggered Dependabot check");
    }

    private Status waitForDependabotUpdates(String githubServerTime) {
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
