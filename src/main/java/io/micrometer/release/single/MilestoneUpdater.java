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

import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.single.MilestoneMigrator.Milestone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MilestoneUpdater {

    private static final Logger log = LoggerFactory.getLogger(MilestoneUpdater.class);

    private final ProcessRunner processRunner;

    private final String githubRepository;

    private final MilestoneMigrator milestoneMigrator;

    MilestoneUpdater(ProcessRunner processRunner, String githubRepository, MilestoneMigrator milestoneMigrator) {
        this.processRunner = processRunner;
        this.githubRepository = githubRepository;
        this.milestoneMigrator = milestoneMigrator;
    }

    MilestoneUpdater(ProcessRunner processRunner) {
        this.githubRepository = processRunner.getOrgRepo();
        this.processRunner = processRunner;
        this.milestoneMigrator = new MilestoneMigrator(this.processRunner, new MilestoneIssueReassigner(processRunner));
    }

    MilestoneWithDeadline updateMilestones(String githubRefName) {
        return this.milestoneMigrator.migrateMilestones(githubRefName);
    }

    void closeMilestone(String githubRefName) {
        log.info("Closing milestone...");
        String milestoneName = githubRefName.replace("v", "");
        Milestone milestone = milestoneMigrator.findMilestone(milestoneName);

        if (milestone != null) {
            processRunner.run("gh", "api", "-X", "PATCH",
                    "/repos/" + githubRepository + "/milestones/" + milestone.number(), "-f", "state=closed");
            log.info("Successfully closed milestone {}", milestoneName);
        }
        else {
            log.warn("No open milestone found with name {}", milestoneName);
        }
    }

}
