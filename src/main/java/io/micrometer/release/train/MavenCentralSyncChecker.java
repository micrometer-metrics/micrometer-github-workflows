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

import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;

class MavenCentralSyncChecker {

    private static final Logger log = LoggerFactory.getLogger(MavenCentralSyncChecker.class);

    private static final String CENTRAL_URL = "https://repo.maven.apache.org/maven2/io/micrometer/";

    private static final int MAX_WAIT_TIME_MINUTES = 20;

    private static final int POLL_INTERVAL_SECONDS = 60;

    private static final int THREAD_POOL_SIZE = 5;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);

    private final String externalUrl;

    private final int maxWaitTimeInMs;

    private final int pollIntervalInMs;

    MavenCentralSyncChecker() {
        this.externalUrl = System.getenv("CENTRAL_URL") != null ? System.getenv("CENTRAL_URL") : CENTRAL_URL;
        this.maxWaitTimeInMs = MAX_WAIT_TIME_MINUTES * 60 * 1000;
        this.pollIntervalInMs = POLL_INTERVAL_SECONDS * 1000;
    }

    // for tests
    MavenCentralSyncChecker(String externalUrl, int maxWaitTimeInMs, int pollIntervalInMs) {
        this.externalUrl = externalUrl;
        this.maxWaitTimeInMs = maxWaitTimeInMs;
        this.pollIntervalInMs = pollIntervalInMs;
    }

    void checkIfArtifactsAreInCentral(List<String> versions, ProjectSetup projectSetup) {
        try {
            List<CompletableFuture<Void>> mavenCheckTasks = versions.stream()
                .map(s -> checkMavenCentralWithRetries(s, projectSetup))
                .toList();
            FutureUtility.waitForTasksToComplete(mavenCheckTasks);
            log.info("Maven Central verification completed.");
        }
        finally {
            scheduler.shutdown();
        }
    }

    private CompletableFuture<Void> checkMavenCentralWithRetries(String version, ProjectSetup projectSetup) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String mavenUrl = externalUrl + projectSetup.artifactToCheck() + "/" + version + "/";
        log.info(
                "Starting Maven Central sync check for version: [{}] and url [{}]. Will check for the artifact every [{}] ms for at most [{}] ms",
                version, mavenUrl, pollIntervalInMs, maxWaitTimeInMs);
        long startTime = System.currentTimeMillis();
        long maxWaitTimeMillis = TimeUnit.SECONDS.toMillis(maxWaitTimeInMs);
        final ScheduledFuture<?> scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() - startTime > maxWaitTimeMillis) {
                log.error("Version {} not found in Maven Central after {} seconds.", version, maxWaitTimeInMs);
                future.completeExceptionally(new IllegalStateException(
                        "Version " + version + " not found in Maven Central after " + maxWaitTimeInMs + " seconds."));
                return;
            }
            if (checkMavenCentral(mavenUrl, version)) {
                log.info("Version {} is available in Maven Central.", version);
                future.complete(null);
            }
            else {
                log.info("Version {} not yet available. Retrying in {} seconds...", version, pollIntervalInMs);
            }
        }, 0, pollIntervalInMs, TimeUnit.SECONDS);
        future.whenComplete((result, throwable) -> scheduledFuture.cancel(true));
        return future;
    }

    private boolean checkMavenCentral(String mavenUrl, String version) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URI(mavenUrl).toURL().openConnection();
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            log.debug("Got response code [{}] from Maven Central", responseCode);
            return responseCode != 404;
        }
        catch (Exception e) {
            log.warn("Failed to verify Maven Central for version: {}. Retrying...", version, e);
            return false;
        }
    }

}
