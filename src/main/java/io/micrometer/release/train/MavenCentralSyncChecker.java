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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;

class MavenCentralSyncChecker {

    private static final Logger log = LoggerFactory.getLogger(MavenCentralSyncChecker.class);

    private static final String CENTRAL_URL = "https://repo.maven.apache.org/maven2/io/micrometer/";

    private final String artifactToCheck;

    private static final int MAX_WAIT_TIME_MINUTES = 20;

    private static final int POLL_INTERVAL_SECONDS = 60;

    private static final int THREAD_POOL_SIZE = 5;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);

    private final String externalUrl;

    private final int maxWaitTimeInSeconds;

    private final int pollIntervalInSeconds;

    MavenCentralSyncChecker(String artifactToCheck) {
        this.artifactToCheck = artifactToCheck;
        this.externalUrl = System.getenv("CENTRAL_URL") != null ? System.getenv("CENTRAL_URL") : CENTRAL_URL;
        this.maxWaitTimeInSeconds = MAX_WAIT_TIME_MINUTES * 60;
        this.pollIntervalInSeconds = POLL_INTERVAL_SECONDS;
    }

    // for tests
    MavenCentralSyncChecker(String artifactToCheck, String externalUrl, int maxWaitTimeInSeconds,
            int pollIntervalInSeconds) {
        this.artifactToCheck = artifactToCheck;
        this.externalUrl = externalUrl;
        this.maxWaitTimeInSeconds = maxWaitTimeInSeconds;
        this.pollIntervalInSeconds = pollIntervalInSeconds;
    }

    void checkIfArtifactsAreInCentral(List<String> versions) {
        try {
            List<CompletableFuture<Void>> mavenCheckTasks = versions.stream()
                .map(this::checkMavenCentralWithRetries)
                .toList();
            FutureUtility.waitForTasksToComplete(mavenCheckTasks);
            log.info("Maven Central verification completed.");
        }
        finally {
            scheduler.shutdown();
        }
    }

    private CompletableFuture<Void> checkMavenCentralWithRetries(String version) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String mavenUrl = externalUrl + artifactToCheck + "/" + version + "/";
        log.info(
                "Starting Maven Central sync check for version: [{}] and url [{}]. Will check for the artifact every [{}] seconds for at most [{}] seconds",
                version, mavenUrl, pollIntervalInSeconds, maxWaitTimeInSeconds);
        long startTime = System.currentTimeMillis();
        long maxWaitTimeMillis = TimeUnit.SECONDS.toMillis(maxWaitTimeInSeconds);
        final ScheduledFuture<?> scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
            if (System.currentTimeMillis() - startTime > maxWaitTimeMillis) {
                log.error("Version {} not found in Maven Central after {} seconds.", version, maxWaitTimeInSeconds);
                future.completeExceptionally(new IllegalStateException("Version " + version
                        + " not found in Maven Central after " + maxWaitTimeInSeconds + " seconds."));
                return;
            }
            if (checkMavenCentral(mavenUrl, version)) {
                log.info("Version {} is available in Maven Central.", version);
                future.complete(null);
            }
            else {
                log.info("Version {} not yet available. Retrying in {} seconds...", version, pollIntervalInSeconds);
            }
        }, 0, pollIntervalInSeconds, TimeUnit.SECONDS);
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
