/**
 * Copyright 2025 the original author or authors.
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

import io.micrometer.release.common.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class ReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReleaseScheduler.class);

    private final CircleCiChecker circleCiChecker;

    private final ProcessRunner processRunner;

    ReleaseScheduler(CircleCiChecker circleCiChecker, ProcessRunner processRunner) {
        this.circleCiChecker = circleCiChecker;
        this.processRunner = processRunner;
    }

    void runReleaseAndCheckCi(Map<String, String> versionToBranch) {
        List<CompletableFuture<Void>> releaseTasks = versionToBranch.entrySet()
            .stream()
            .map(entry -> CompletableFuture.runAsync(() -> handleReleaseAndCI(entry.getKey(), entry.getValue())))
            .toList();
        FutureUtility.waitForTasksToComplete(releaseTasks);
        log.info("All releases created and CI checks completed successfully.");
    }

    private void handleReleaseAndCI(String version, String branch) {
        try {
            createGithubRelease(version, branch);
            boolean buildSuccessful = circleCiChecker.checkBuildStatus(version);
            if (!buildSuccessful) {
                throw new IllegalStateException("Build failed for version: " + version);
            }
        }
        catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed for version: " + version + ". Error: " + e.getMessage(), e);
        }
    }

    private void createGithubRelease(String version, String branch) throws IOException, InterruptedException {
        log.info("Creating GitHub release for version: [{}]  from branch: [{}]", version, branch);
        processRunner.run("gh", "release", "create", "v" + version, "--target", branch, "-t", version);
    }

}
