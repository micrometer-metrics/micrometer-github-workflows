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

import io.micrometer.release.MilestoneMigrator.Milestone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.Mockito.*;

class MilestoneIssueReassignerTests {

    private static final String GH_REPO = "micrometer-metrics/build-test";

    private static final int CLOSED_ISSUE_ID = 2;

    private static final int OPEN_ISSUE_ID = 3;

    ProcessRunner processRunner = mock();

    MilestoneIssueReassigner reasigner = new MilestoneIssueReassigner(processRunner, GH_REPO) {
        @Override
        LocalDate currentDate() {
            return LocalDate.of(2025, 1, 27); // Last monday of the month
        }
    };

    private void setupProcessRunner(String version) {
        when(processRunner.run("gh", "api", "/repos/" + GH_REPO + "/milestones", "-X", "POST", "-f", "title=" + version,
                "-f", "due_on=" + LocalDate.of(2025, 2, 10) + "T17:00:00Z"))
            .thenReturn(Collections.singletonList("{\"number\":5,\"title\":\"1.0.2\"}"));
    }

    @Test
    void should_reassign_issues_to_a_milestone_for_non_ga_release() {
        String version = "1.0.0-M1";
        Milestone concreteMilestone = new Milestone(1, version);
        setupProcessRunner("1.0.0-M2");

        MilestoneWithDeadline milestoneWithDeadline = reasigner.reassignIssues(concreteMilestone, "v" + version,
                Collections.singletonList(CLOSED_ISSUE_ID), Collections.singletonList(OPEN_ISSUE_ID));

        thenMilestoneHasProperDeadline(milestoneWithDeadline, "1.0.0-M2");
        thenClosedIssuesFromGenericMilestoneGotMovedToConcreteMilestone(concreteMilestone);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0.0-SNAPSHOT", "1.0.0-M4", "1.0.0-RC2" })
    void should_fail_for_non_matching_versions(String version) {
        Milestone concreteMilestone = new Milestone(1, version);

        thenThrownBy(() -> reasigner.reassignIssues(concreteMilestone, "v" + version,
                Collections.singletonList(CLOSED_ISSUE_ID), Collections.singletonList(OPEN_ISSUE_ID)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(
                    "Milestone title [" + version + "] contains invalid format (we accept M[1-3] or RC1)");
    }

    @Test
    void should_reassign_issues_to_a_milestone_and_create_a_new_milestone_for_ga_release() {
        String version = "1.0.1";
        setupProcessRunner("1.0.2");
        Milestone concreteMilestone = new Milestone(1, version);

        MilestoneWithDeadline milestoneWithDeadline = reasigner.reassignIssues(concreteMilestone, "v" + version,
                Collections.singletonList(CLOSED_ISSUE_ID), Collections.singletonList(OPEN_ISSUE_ID));

        thenMilestoneHasProperDeadline(milestoneWithDeadline, "1.0.2");
        thenClosedIssuesFromGenericMilestoneGotMovedToConcreteMilestone(concreteMilestone);
    }

    private static void thenMilestoneHasProperDeadline(MilestoneWithDeadline milestoneWithDeadline, String title) {
        then(milestoneWithDeadline.id()).isEqualTo(5);
        then(milestoneWithDeadline.title()).isEqualTo(title);
        then(milestoneWithDeadline.deadline().toString()).isEqualTo("2025-02-10");
    }

    private void thenClosedIssuesFromGenericMilestoneGotMovedToConcreteMilestone(Milestone concreteMilestone) {
        verify(processRunner).run("gh", "api", String.format("/repos/%s/issues/%d", GH_REPO, CLOSED_ISSUE_ID), "-X",
                "PATCH", "-f", String.format("milestone=%d", concreteMilestone.number()));
    }

}
