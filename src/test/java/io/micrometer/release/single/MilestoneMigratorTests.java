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

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.single.MilestoneMigrator.Milestone;

import java.time.LocalDate;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class MilestoneMigratorTests {

    private static final String GH_REPO = "micrometer-metrics/build-test";

    ProcessRunner runner = mock();

    MilestoneIssueReassigner reasigner = mock();

    MilestoneMigrator migrator = new MilestoneMigrator(runner, GH_REPO, reasigner);

    @Test
    void should_throw_exception_when_no_milestone_found() {
        thenThrownBy(() -> migrator.migrateMilestones("v1.0.0")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No response from gh cli for version <1.0.0>");
    }

    @Test
    void should_throw_exception_when_no_milestone_found_in_github_response() {
        when(runner.run(any(String[].class))).thenReturn(Collections.singletonList(""));
        thenThrownBy(() -> migrator.migrateMilestones("v1.0.0")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not find milestone for <v1.0.0>");

        when(runner.run(any(String[].class))).thenReturn(Collections.singletonList(null));
        thenThrownBy(() -> migrator.migrateMilestones("v1.0.0")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not find milestone for <v1.0.0>");
    }

    @Test
    void should_throw_exception_when_no_generic_milestone_found() {
        when(runner.run(any(String[].class)))
            .thenReturn(Collections.singletonList("{\"number\":5,\"title\":\"1.0.0\"}"))
            .thenReturn(Collections.singletonList(""));

        thenThrownBy(() -> migrator.migrateMilestones("v1.0.0")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not find generic milestone <1.0.x>");
    }

    @Test
    void should_reassign_issues_from_generic_milestone_to_concrete_one() {
        String concrete = "1.0.0";
        String generic = "1.0.x";
        when(runner.run("gh", "api", "/repos/" + GH_REPO + "/milestones", "--jq",
                String.format(".[] | select(.title == \"%s\") | {number: .number, title: .title}", concrete)))
            .thenReturn(Collections.singletonList("{\"number\":5,\"title\":\"" + concrete + "\"}")); // concrete
        when(runner.run("gh", "api", "/repos/" + GH_REPO + "/milestones", "--jq",
                String.format(".[] | select(.title == \"%s\") | {number: .number, title: .title}", generic)))
            .thenReturn(Collections.singletonList("{\"number\":4,\"title\":\"" + generic + "\"}")); // generic
        when(runner.run("gh", "api", String.format("/repos/%s/issues?milestone=%d&state=all", GH_REPO, 5), "--jq",
                ".[] | {number: .number, state: .state}"))
            .thenReturn(Collections.singletonList("{\"number\":10,\"state\":\"open\"}")); // concrete
        when(runner.run("gh", "api", String.format("/repos/%s/issues?milestone=%d&state=all", GH_REPO, 4), "--jq",
                ".[] | {number: .number, state: .state}"))
            .thenReturn(Collections.singletonList("{\"number\":11,\"state\":\"closed\"}")); // generic
        MilestoneWithDeadline expectedMilestone = new MilestoneWithDeadline(12, "1.0.1", LocalDate.of(2025, 1, 1));
        when(reasigner.reassignIssues(new Milestone(5, concrete), "v" + concrete, Collections.singletonList(11),
                Collections.singletonList(10)))
            .thenReturn(expectedMilestone);

        MilestoneWithDeadline withDeadline = migrator.migrateMilestones("v1.0.0");

        then(withDeadline).isSameAs(expectedMilestone);
    }

}
