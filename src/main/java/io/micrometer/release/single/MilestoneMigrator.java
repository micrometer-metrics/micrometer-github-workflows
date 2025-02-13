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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MilestoneMigrator {

    private static final Logger log = LoggerFactory.getLogger(MilestoneMigrator.class);

    private final ProcessRunner processRunner;

    private final String ghRepo;

    private final MilestoneIssueReassigner milestoneIssueReassigner;

    MilestoneMigrator(ProcessRunner processRunner, String ghRepo, MilestoneIssueReassigner milestoneIssueReassigner) {
        this.processRunner = processRunner;
        this.ghRepo = ghRepo;
        this.milestoneIssueReassigner = milestoneIssueReassigner;
    }

    MilestoneWithDeadline migrateMilestones(String refName) {
        log.info("Migrating milestones");
        // Find concrete milestone (e.g. 1.14.4)
        String title = refName.startsWith("v") ? refName.substring(1) : refName;
        Milestone concreteMilestone = findMilestone(title);
        if (concreteMilestone == null) {
            throw new IllegalStateException("Could not find milestone for <" + refName + ">");
        }
        log.info("Found concrete milestone with id [{}] and title [{}]", concreteMilestone.number,
                concreteMilestone.title);

        // Find generic milestone (e.g. 1.14.x)
        String genericRef = refName.substring(0, refName.lastIndexOf('.')) + ".x";
        String genericTitle = genericRef.startsWith("v") ? genericRef.substring(1) : genericRef;
        Milestone genericMilestone = findMilestone(genericTitle);
        if (genericMilestone == null) {
            throw new IllegalStateException("Could not find generic milestone <" + genericTitle + ">");
        }
        log.info(
                "For ref [{}] and generic version [{}] found a corresponding generic milestone with id [{}] and title [{}]",
                refName, genericTitle, genericMilestone.number, genericMilestone.title);
        return reassignIssues(refName, genericMilestone, concreteMilestone);
    }

    private MilestoneWithDeadline reassignIssues(String refName, Milestone genericMilestone,
            Milestone concreteMilestone) {
        // Get all issues from generic milestone
        List<Issue> concreteIssues = getIssuesForMilestone(concreteMilestone.number());
        List<Issue> genericIssues = getIssuesForMilestone(genericMilestone.number());

        // Split issues by state
        List<Integer> closedIssues = new ArrayList<>();
        List<Integer> openIssues = new ArrayList<>();
        // Move closed issues in generic to next release
        for (Issue issue : genericIssues) {
            if ("closed".equals(issue.state())) {
                closedIssues.add(issue.number());
            }
        }
        // Move open issues in current to next release
        for (Issue issue : concreteIssues) {
            if ("open".equals(issue.state())) {
                openIssues.add(issue.number());
            }
        }

        return milestoneIssueReassigner.reassignIssues(concreteMilestone, refName, closedIssues, openIssues);
    }

    private Milestone findMilestone(String title) {
        List<String> lines = processRunner.run("gh", "api", "/repos/" + ghRepo + "/milestones", "--jq",
                String.format(".[] | select(.title == \"%s\") | {number: .number, title: .title}", title));
        if (lines.isEmpty()) {
            throw new IllegalStateException("No response from gh cli for version <" + title + ">");
        }
        String line = lines.get(0);
        if (line == null || line.isBlank()) {
            return null;
        }
        // Parse JSON manually since it's a simple structure
        int number = Integer.parseInt(line.split("\"number\":")[1].split(",")[0].trim());
        String milestoneTitle = line.split("\"title\":\"")[1].split("\"")[0];
        return new Milestone(number, milestoneTitle);
    }

    private List<Issue> getIssuesForMilestone(int milestoneNumber) {
        List<String> lines = processRunner.run("gh", "api",
                String.format("/repos/%s/issues?milestone=%d&state=all", ghRepo, milestoneNumber), "--jq",
                ".[] | {number: .number, state: .state}");
        List<Issue> issues = new ArrayList<>();
        for (String line : lines) {
            if (!line.isBlank()) {
                int number = Integer.parseInt(line.split("\"number\":")[1].split(",")[0].trim());
                String state = line.split("\"state\":\"")[1].split("\"")[0];
                issues.add(new Issue(number, state));
            }
        }
        return issues;
    }

    record Issue(int number, String state) {

    }

    record Milestone(int number, String title) {

    }

}
