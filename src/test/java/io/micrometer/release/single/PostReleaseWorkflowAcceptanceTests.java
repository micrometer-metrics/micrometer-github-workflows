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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.release.common.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// Requires access to Github to download
class PostReleaseWorkflowAcceptanceTests {

    @RegisterExtension
    static WireMockExtension wm1 = WireMockExtension.newInstance().options(wireMockConfig().port(60006)).build();

    Path tmpDir = Files.createTempDirectory("micrometer-release");

    File outputJar = new File(tmpDir.toFile(), "generator.jar");

    File outputChangelog = new File(tmpDir.toFile(), "output.md");

    File oldOutputChangelog = new File(
            AssertingReleaseNotesUpdater.class.getResource("/processor/micrometer-1.13.9-output.md").toURI());

    MilestoneUpdater milestoneUpdater = mock();

    AssertingReleaseNotesUpdater updater = new AssertingReleaseNotesUpdater();

    PostReleaseWorkflowAcceptanceTests() throws IOException, URISyntaxException {
    }

    @Test
    void should_perform_full_post_release_process() throws Exception {
        PostReleaseWorkflow postReleaseWorkflow = testPostReleaseWorkflow(updater);

        postReleaseWorkflow.run("v1.14.0", "v1.13.9");

        then(updater.wasCalled).as("ReleaseNotesUpdater must be called").isTrue();
        verify(milestoneUpdater).closeMilestone("v1.14.0");
        NotificationSenderTests.assertThatNotificationGotSent(wm1);
    }

    private PostReleaseWorkflow testPostReleaseWorkflow(AssertingReleaseNotesUpdater updater) {
        return new PostReleaseWorkflow(
                new ChangelogGeneratorDownloader(ChangelogGeneratorDownloader.CHANGELOG_GENERATOR_URL, outputJar),
                ChangelogGeneratorTests.testChangelogGenerator(outputChangelog),
                ChangelogFetcherTests.testChangelogFetcher(oldOutputChangelog),
                ChangelogProcessorTests.testChangelogProcessor(outputChangelog), updater, milestoneUpdater,
                NotificationSenderTests.testNotificationSender(wm1),
                new ProcessRunner("micrometer-metrics/micrometer"));
    }

    static class AssertingReleaseNotesUpdater extends ReleaseNotesUpdater {

        File expectedOutput = new File(
                AssertingReleaseNotesUpdater.class.getResource("/processor/micrometer-1.14.0-output.md").toURI());

        private boolean wasCalled;

        AssertingReleaseNotesUpdater() throws URISyntaxException {
            super(new ProcessRunner("micrometer-metrics/micrometer"));
        }

        @Override
        void updateReleaseNotes(String githubRef, File changelog) {
            wasCalled = true;

            try {
                then(Files.readString(changelog.toPath()))
                    .isEqualToIgnoringNewLines(Files.readString(expectedOutput.toPath()));
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
