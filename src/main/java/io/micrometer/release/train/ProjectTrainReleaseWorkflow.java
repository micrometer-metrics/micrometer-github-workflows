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
import io.micrometer.release.common.Input;
import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.single.PostReleaseWorkflow;

import io.micrometer.release.train.TrainOptions.ProjectSetup;

import java.net.http.HttpClient;
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

    public ProjectTrainReleaseWorkflow(String githubOrgRepo, ProcessRunner processRunner,
            PostReleaseWorkflow postReleaseWorkflow) {
        this.releaseScheduler = new ReleaseScheduler(
                new CircleCiChecker(System.getenv("CIRCLE_CI_TOKEN"), githubOrgRepo, HTTP_CLIENT, OBJECT_MAPPER),
                processRunner);
        this.versionToBranchConverter = new VersionToBranchConverter(Input.getGhToken(),
                "https://api.github.com/repos/" + githubOrgRepo + "/branches/", HTTP_CLIENT);
        this.postReleaseTaskScheduler = new PostReleaseTaskScheduler(postReleaseWorkflow, new Git(processRunner),
                githubOrgRepo);
        this.mavenCentralSyncChecker = new MavenCentralSyncChecker();
    }

    // For tests
    ProjectTrainReleaseWorkflow(ReleaseScheduler releaseScheduler, VersionToBranchConverter versionToBranchConverter,
            PostReleaseTaskScheduler postReleaseTaskScheduler, MavenCentralSyncChecker mavenCentralSyncChecker) {
        this.releaseScheduler = releaseScheduler;
        this.versionToBranchConverter = versionToBranchConverter;
        this.postReleaseTaskScheduler = postReleaseTaskScheduler;
        this.mavenCentralSyncChecker = mavenCentralSyncChecker;
    }

    public void run(ProjectSetup projectSetup) {
        List<String> versions = projectSetup.versionsForThisProject();
        Map<String, String> versionToBranch = versionToBranchConverter.convert(versions);
        releaseScheduler.runReleaseAndCheckCi(versionToBranch, projectSetup);
        postReleaseTaskScheduler.runPostReleaseTasks(versions);
        mavenCentralSyncChecker.checkIfArtifactsAreInCentral(versions, projectSetup);
    }

}
