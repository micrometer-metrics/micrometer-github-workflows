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

import io.micrometer.release.common.Input;
import io.micrometer.release.common.ProcessRunner;

import java.io.File;

public class PostReleaseWorkflow {

    private final ChangelogGeneratorDownloader changelogGeneratorDownloader;

    private final ChangelogGenerator changelogGenerator;

    private final ChangelogFetcher changelogFetcher;

    private final ChangelogProcessor changelogProcessor;

    private final ReleaseNotesUpdater releaseNotesUpdater;

    private final MilestoneUpdater milestoneUpdater;

    private final NotificationSender notificationSender;

    private final ProcessRunner processRunner;

    PostReleaseWorkflow(ChangelogGeneratorDownloader changelogGeneratorDownloader,
            ChangelogGenerator changelogGenerator, ChangelogFetcher changelogFetcher,
            ChangelogProcessor changelogProcessor, ReleaseNotesUpdater releaseNotesUpdater,
            MilestoneUpdater milestoneUpdater, NotificationSender notificationSender, ProcessRunner processRunner) {
        this.changelogGeneratorDownloader = changelogGeneratorDownloader;
        this.changelogGenerator = changelogGenerator;
        this.changelogFetcher = changelogFetcher;
        this.changelogProcessor = changelogProcessor;
        this.releaseNotesUpdater = releaseNotesUpdater;
        this.milestoneUpdater = milestoneUpdater;
        this.notificationSender = notificationSender;
        this.processRunner = processRunner;
    }

    public PostReleaseWorkflow(ProcessRunner processRunner) {
        this(new ChangelogGeneratorDownloader(), new ChangelogGenerator(processRunner),
                new ChangelogFetcher(processRunner), new ChangelogProcessor(processRunner),
                new ReleaseNotesUpdater(processRunner), new MilestoneUpdater(processRunner), new NotificationSender(),
                processRunner);
    }

    // micrometer-metrics/tracing
    // v1.3.1
    // v1.2.5 (optional)
    public void run(String githubRefName, String previousRefName) {
        String githubOrgRepo = processRunner.getOrgRepo();
        Input.assertInputs(githubOrgRepo, githubRefName, previousRefName);
        String githubRepo = githubOrgRepo.contains("/") ? githubOrgRepo.split("/")[1] : githubOrgRepo;

        // Close milestone and move issues around
        MilestoneWithDeadline newMilestoneId = updateMilestones(githubRefName);

        // Download GitHub Changelog Generator
        File changelogJar = downloadChangelogGenerator();

        // Generate current changelog
        File changelog = generateChangelog(githubRefName, githubOrgRepo, changelogJar);

        File oldChangelog = null;
        // If previousRefName present - fetch its changelog
        if (previousRefName != null && !previousRefName.isBlank()) {
            oldChangelog = generateOldChangelog(previousRefName);
        }

        // Process changelog
        File outputChangelog = processChangelog(changelog, oldChangelog);

        // Update release notes
        updateReleaseNotes(githubRefName, outputChangelog);

        // Send notifications
        sendNotifications(githubRepo, githubRefName, newMilestoneId);
    }

    private File downloadChangelogGenerator() {
        try {
            return changelogGeneratorDownloader.downloadChangelogGenerator();
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private File generateOldChangelog(String githubRefName) {
        return changelogFetcher.fetchChangelog(githubRefName);
    }

    private File generateChangelog(String githubRefName, String githubOrgRepo, File jarPath) {
        return changelogGenerator.generateChangelog(githubRefName, githubOrgRepo, jarPath);
    }

    private File processChangelog(File changelog, File oldChangelog) {
        try {
            return changelogProcessor.processChangelog(changelog, oldChangelog);
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
