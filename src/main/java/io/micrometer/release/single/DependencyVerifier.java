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
package io.micrometer.release.single;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

import io.micrometer.release.common.ProcessRunner;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

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

    boolean verifyDependencies(String orgRepository) {
        String githubServerTime = getGitHubServerTime();
        triggerDependabotCheck(orgRepository);
        log.info("Waiting {} {} for PRs to be created...", initialWait, timeUnit);
        sleep(initialWait);
        return waitForDependabotUpdates(githubServerTime);
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

    private boolean waitForDependabotUpdates(String githubServerTime) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeUnit.toMillis(timeout);
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            List<String> openPRs = getOpenMicrometerDependabotPRs(githubServerTime);
            if (openPRs.isEmpty()) {
                log.info("No pending Micrometer updates");
                return true;
            }
            boolean allProcessed = true;
            for (String pr : openPRs) {
                if (!checkPRStatus(pr)) {
                    allProcessed = false;
                }
            }
            if (allProcessed) {
                log.info("All Dependabot PRs processed");
                return true;
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
