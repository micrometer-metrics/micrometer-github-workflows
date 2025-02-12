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

class PostReleaseWorkflow {

    private final ChangelogGeneratorDownloader changelogGeneratorDownloader;

    private final ChangelogGenerator changelogGenerator;

    private final ChangelogFetcher changelogFetcher;

    private final ChangelogProcessor changelogProcessor;

    private final ReleaseNotesUpdater releaseNotesUpdater;

    private final MilestoneUpdater milestoneUpdater;

    private final NotificationSender notificationSender;

    PostReleaseWorkflow(ChangelogGeneratorDownloader changelogGeneratorDownloader,
            ChangelogGenerator changelogGenerator, ChangelogFetcher changelogFetcher,
            ChangelogProcessor changelogProcessor, ReleaseNotesUpdater releaseNotesUpdater,
            MilestoneUpdater milestoneUpdater, NotificationSender notificationSender) {
        this.changelogGeneratorDownloader = changelogGeneratorDownloader;
        this.changelogGenerator = changelogGenerator;
        this.changelogFetcher = changelogFetcher;
        this.changelogProcessor = changelogProcessor;
        this.releaseNotesUpdater = releaseNotesUpdater;
        this.milestoneUpdater = milestoneUpdater;
        this.notificationSender = notificationSender;
    }

    public void run() throws Exception {
        String githubOrgRepo = ghOrgRepo(); // micrometer-metrics/tracing
        String githubRefName = ghRef(); // v1.3.1
        String previousRefName = previousRefName(); // v1.2.5
        assertInputs(githubOrgRepo, githubRefName, previousRefName);

        String githubRepo = githubOrgRepo.contains("/") ? githubOrgRepo.split("/")[1] : githubOrgRepo;

        // Step 1: Close milestone and move issues around
        MilestoneWithDeadline newMilestoneId = updateMilestones(githubRefName);

        // Step 2: Download GitHub Changelog Generator
        File changelogJar = downloadChangelogGenerator();

        // Step 3: Generate current changelog
        File changelog = generateChangelog(githubRefName, githubOrgRepo, changelogJar);

        File oldChangelog = null;
        // Step 3a: If previousRefName present - fetch its changelog
        if (previousRefName != null && !previousRefName.isBlank()) {
            oldChangelog = generateOldChangelog(previousRefName, githubOrgRepo);
        }

        // Step 4: Process changelog
        File outputChangelog = processChangelog(changelog, oldChangelog);

        // Step 5: Update release notes
        updateReleaseNotes(githubRefName, outputChangelog);

        // Step 6: Send notifications
        sendNotifications(githubRepo, githubRefName, newMilestoneId);
    }

    void assertInputs(String githubOrgRepo, String githubRefName, String previousRefName) {
        if (githubOrgRepo == null) {
            throw new IllegalStateException("No repo found, please provide the GITHUB_REPOSITORY env variable");
        }
        if (githubRefName == null) {
            throw new IllegalStateException("No github ref found, please provide the GITHUB_REF_NAME env variable");
        }
        if (!githubRefName.startsWith("v")) {
            throw new IllegalStateException("Github ref must be a tag (must start with 'v'): " + githubRefName);
        }
        if (previousRefName != null && !previousRefName.isBlank() && !previousRefName.startsWith("v")) {
            throw new IllegalStateException(
                    "Previous github ref must be a tag (must start with 'v'): " + previousRefName);
        }
    }

    String ghRef() {
        return System.getenv("GITHUB_REF_NAME");
    }

    String ghOrgRepo() {
        return System.getenv("GITHUB_REPOSITORY");
    }

    String previousRefName() {
        return System.getenv("PREVIOUS_REF_NAME");
    }

    private File downloadChangelogGenerator() throws Exception {
        return changelogGeneratorDownloader.downloadChangelogGenerator();
    }

    private File generateOldChangelog(String githubRefName, String githubOrgRepo) {
        return changelogFetcher.fetchChangelog(githubRefName, githubOrgRepo);
    }

    private File generateChangelog(String githubRefName, String githubOrgRepo, File jarPath) {
        return changelogGenerator.generateChangelog(githubRefName, githubOrgRepo, jarPath);
    }

    private File processChangelog(File changelog, File oldChangelog) throws Exception {
        return changelogProcessor.processChangelog(changelog, oldChangelog);
    }

    private void updateReleaseNotes(String refName, File changelog) {
        releaseNotesUpdater.updateReleaseNotes(refName, changelog);
    }

    private MilestoneWithDeadline updateMilestones(String refName) {
        MilestoneWithDeadline newMilestone = milestoneUpdater.updateMilestones(refName);
        milestoneUpdater.closeMilestone(refName);
        return newMilestone;
    }

    private void sendNotifications(String repoName, String refName, MilestoneWithDeadline newMilestoneId) {
        notificationSender.sendNotifications(repoName, refName, newMilestoneId);
    }

}
