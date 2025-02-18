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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.single.PostReleaseWorkflow;

import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ProjectTrainReleaseWorkflow {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ReleaseScheduler releaseScheduler;

    private final VersionToBranchConverter versionToBranchConverter;

    private final PostReleaseTaskScheduler postReleaseTaskScheduler;

    private final MavenCentralSyncChecker mavenCentralSyncChecker;

    public ProjectTrainReleaseWorkflow(String githubOrgRepo, String artifactToCheck, ProcessRunner processRunner,
            PostReleaseWorkflow postReleaseWorkflow) {
        this.releaseScheduler = new ReleaseScheduler(
                new CircleCiChecker(System.getenv("CIRCLE_CI_TOKEN"), githubOrgRepo, HTTP_CLIENT, OBJECT_MAPPER),
                processRunner);
        this.versionToBranchConverter = new VersionToBranchConverter(System.getenv("GH_TOKEN"),
                "https://api.github.com/repos/" + githubOrgRepo + "/branches/", HTTP_CLIENT);
        this.postReleaseTaskScheduler = new PostReleaseTaskScheduler(postReleaseWorkflow, new Git(processRunner),
                githubOrgRepo);
        this.mavenCentralSyncChecker = new MavenCentralSyncChecker(artifactToCheck);
    }

    // For tests
    ProjectTrainReleaseWorkflow(ReleaseScheduler releaseScheduler, VersionToBranchConverter versionToBranchConverter,
            PostReleaseTaskScheduler postReleaseTaskScheduler, MavenCentralSyncChecker mavenCentralSyncChecker) {
        this.releaseScheduler = releaseScheduler;
        this.versionToBranchConverter = versionToBranchConverter;
        this.postReleaseTaskScheduler = postReleaseTaskScheduler;
        this.mavenCentralSyncChecker = mavenCentralSyncChecker;
    }

    public void run(String commaSeparatedListOfVersions) {
        // Take in a comma separated list of versions (e.g. 1.15.6-M2, 1.14.9, 1.13.2)
        // Iterate over the list, convert version to branch (e.g. 1.15.6-M2 -> 1.15.x or
        // main if doesn't exist)
        // Using GH cli create a release and tag from each branch (main, 1.14.x, 1.13.x ->
        // v1.15.6-M2, v1.14.9, v1.13.2)
        // Check if circle ci status is successful for all executions
        // Once all jobs are completed that means that all 3 deployments completed
        // successfully (now we're waiting for sync to central)
        // For each branch now run a post release action
        // Finally check if sync is finished
        List<String> versions = Arrays.stream(commaSeparatedListOfVersions.split(",")).map(String::trim).toList();
        Map<String, String> versionToBranch = versionToBranchConverter.convert(versions);
        releaseScheduler.runReleaseAndCheckCi(versionToBranch);
        postReleaseTaskScheduler.runPostReleaseTasks(versions);
        mavenCentralSyncChecker.checkIfArtifactsAreInCentral(versions);
    }

}
