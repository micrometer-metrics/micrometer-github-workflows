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
package io.micrometer.release;

import java.io.File;

public class PostReleaseWorkflow {

    private final ChangelogGeneratorDownloader changelogGeneratorDownloader;

    private final ChangelogGenerator changelogGenerator;

    private final ChangelogProcessor changelogProcessor;

    private final ReleaseNotesUpdater releaseNotesUpdater;

    private final MilestoneUpdater milestoneUpdater;

    private final NotificationSender notificationSender;

    PostReleaseWorkflow(ChangelogGeneratorDownloader changelogGeneratorDownloader,
            ChangelogGenerator changelogGenerator, ChangelogProcessor changelogProcessor,
            ReleaseNotesUpdater releaseNotesUpdater, MilestoneUpdater milestoneUpdater,
            NotificationSender notificationSender) {
        this.changelogGeneratorDownloader = changelogGeneratorDownloader;
        this.changelogGenerator = changelogGenerator;
        this.changelogProcessor = changelogProcessor;
        this.releaseNotesUpdater = releaseNotesUpdater;
        this.milestoneUpdater = milestoneUpdater;
        this.notificationSender = notificationSender;
    }

    public static void main(String[] args) throws Exception {

        String githubOrgRepo = System.getenv("GITHUB_REPOSITORY"); // micrometer-metrics/tracing
        String githubRepo = githubOrgRepo.contains("/") ? githubOrgRepo.split("/")[1] : githubOrgRepo;
        String githubRefName = System.getenv("GITHUB_REF_NAME");

        PostReleaseWorkflow workflow = newWorkflow();

        // Step 1: Download GitHub Changelog Generator
        File outputJar = workflow.downloadChangelogGenerator();

        // Step 2: Generate changelog
        File changelog = workflow.generateChangelog(outputJar);

        // Step 3: Process changelog
        workflow.processChangelog(changelog);

        // Step 4: Update release notes
        workflow.updateReleaseNotes(githubRefName, changelog);

        // Step 5: Close milestone
        workflow.closeMilestone(githubRefName);

        // Step 6: Send notifications
        workflow.sendNotifications(githubRepo, githubRefName);
    }

    private static PostReleaseWorkflow newWorkflow() {
        return new PostReleaseWorkflow(new ChangelogGeneratorDownloader(),
            new ChangelogGenerator(), new ChangelogProcessor(), new ReleaseNotesUpdater(),
            new MilestoneUpdater(),
            new NotificationSender());
    }

    private File downloadChangelogGenerator() throws Exception {
        return changelogGeneratorDownloader.downloadChangelogGenerator();
    }

    private File generateChangelog(File jarPath) throws Exception {
        return changelogGenerator.generateChangelog(jarPath);
    }

    private void processChangelog(File changelog) throws Exception {
        changelogProcessor.processChangelog(changelog);
    }

    private void updateReleaseNotes(String refName, File changelog) {
        releaseNotesUpdater.updateReleaseNotes(refName, changelog);
    }

    private void closeMilestone(String refName) {
        milestoneUpdater.closeMilestone(refName);
    }

    private void sendNotifications(String repoName, String refName) {
        notificationSender.sendNotifications(repoName, refName);
    }

}
