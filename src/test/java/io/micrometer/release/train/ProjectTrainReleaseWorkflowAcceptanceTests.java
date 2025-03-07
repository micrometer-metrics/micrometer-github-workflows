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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.single.PostReleaseWorkflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.Arrays;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class ProjectTrainReleaseWorkflowAcceptanceTests {

    private static final String GITHUB_ORG_REPO = "micrometer-metrics/micrometer";

    private static final Logger log = LoggerFactory.getLogger(ProjectTrainReleaseWorkflowAcceptanceTests.class);

    @RegisterExtension
    static WireMockExtension wm1 = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    ProcessRunner processRunner = mock(invocation -> {
        if (invocation.getMethod().getName().equals("getOrgRepo")) {
            return GITHUB_ORG_REPO;
        }
        log.info("Process runner command {}", Arrays.toString(invocation.getArguments()));
        return null;
    });

    DependencyVerifier dependencyVerifier = mock();

    PostReleaseWorkflow postReleaseWorkflow = mock();

    CircleCiChecker circleCiChecker = CircleCiCheckerTests.getChecker("success", wm1.url("/api/v2/"));

    MavenCentralSyncChecker mavenCentralSyncChecker = new MavenCentralSyncChecker(wm1.url("/maven2/io/micrometer/"), 3,
            1);

    ReleaseScheduler releaseScheduler = new ReleaseScheduler(circleCiChecker, processRunner, dependencyVerifier);

    PostReleaseTaskScheduler postReleaseTaskScheduler = new PostReleaseTaskScheduler(postReleaseWorkflow,
            new Git(processRunner));

    VersionToBranchConverter versionToBranchConverter = new VersionToBranchConverter("foo",
            wm1.url("/repos/micrometer-metrics/micrometer/branches/"), HttpClient.newBuilder().build());

    ProjectTrainReleaseWorkflow workflow = new ProjectTrainReleaseWorkflow(releaseScheduler, versionToBranchConverter,
            postReleaseTaskScheduler, mavenCentralSyncChecker);

    @Test
    void should_perform_the_release() {
        workflow.run(TestProjectSetup.forMicrometer("1.14.9"));

        thenGithubReleaseAndTagGotCreated();
        thenCiBuildStatusWasVerified();
        thenMavenCentralSyncWasVerified();
    }

    private static void thenCiBuildStatusWasVerified() {
        wm1.verify(WireMock
            .getRequestedFor(WireMock.urlEqualTo("/api/v2/project/github/micrometer-metrics/micrometer/pipeline")));
    }

    private static void thenMavenCentralSyncWasVerified() {
        wm1.verify(WireMock.headRequestedFor(WireMock.urlEqualTo("/maven2/io/micrometer/micrometer-bom/1.14.9/")));
    }

    private void thenGithubReleaseAndTagGotCreated() {
        then(processRunner).should().run("gh", "release", "create", "v1.14.9", "--target", "1.14.x", "-t", "1.14.9");
    }

}
