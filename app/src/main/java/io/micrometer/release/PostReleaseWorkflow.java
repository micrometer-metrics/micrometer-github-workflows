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

import java.io.IOException;

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
        PostReleaseWorkflow workflow = new PostReleaseWorkflow(
            new ChangelogGeneratorDownloader(), new ChangelogGenerator(), new ChangelogProcessor(),
            new ReleaseNotesUpdater(), new MilestoneUpdater(), new NotificationSender());

        // Step 1: Download GitHub Changelog Generator
        workflow.downloadChangelogGenerator();

        // Step 2: Generate changelog
        workflow.generateChangelog();

        // Step 3: Process changelog
        workflow.processChangelog();

        // Step 4: Update release notes
        workflow.updateReleaseNotes();

        // Step 5: Close milestone
        workflow.closeMilestone();

        // Step 6: Send notifications
        workflow.sendNotifications();
    }

    private void downloadChangelogGenerator() throws Exception {
        changelogGeneratorDownloader.downloadChangelogGenerator();
    }

    private void generateChangelog() throws Exception {
        changelogGenerator.generateChangelog();
    }

    private void processChangelog() throws Exception {
        changelogProcessor.processChangelog();
    }

    private void updateReleaseNotes() throws Exception {
        releaseNotesUpdater.updateReleaseNotes();
    }

    private void closeMilestone() throws Exception {
        milestoneUpdater.closeMilestone();
    }

    private void sendNotifications() throws Exception {
        notificationSender.sendNotifications();
    }
}
