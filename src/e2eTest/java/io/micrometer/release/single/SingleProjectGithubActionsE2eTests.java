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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.release.common.GithubActions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SingleProjectGithubActionsE2eTests implements GithubActions {

    @BeforeAll
    static void should_go_through_whole_flow() {
        githubClient.createReleaseAndTag("v0.1.1");
        runPostReleaseWorkflow();
    }

    @Test
    void should_verify_release_notes_content() throws JsonProcessingException {
        Release release = githubClient.getRelease("v0.1.1");

        assertThat(release.body()).containsIgnoringWhitespaces(
                "Closed enhancement in generic 0.1.x [#8](https://github.com/marcingrzejszczak/gh-actions-test/issues/8)")
            .containsIgnoringWhitespaces(
                    "Closed bug in concrete 0.1.1 [#12](https://github.com/marcingrzejszczak/gh-actions-test/issues/12)")
            .containsIgnoringWhitespaces(
                    "Closed bug in generic 0.1.x [#9](https://github.com/marcingrzejszczak/gh-actions-test/issues/9)");
    }

    @Test
    void should_verify_current_milestone() throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle("0.1.1");

        String[] issueTitles = { "Closed issue in generic 0.1.x", "Closed bug in concrete 0.1.1",
                "Closed bug in generic 0.1.x", "Closed enhancement in generic 0.1.x" };
        assertThat(milestone.state()).isEqualTo("closed");
        List<Issue> issues = githubClient.getIssuesForMilestone(milestone.number());
        assertThat(issues).filteredOn(s -> Arrays.asList(issueTitles).contains(s.title()))
            .extracting(Issue::state)
            .containsOnly("closed");
        assertThat(issues).extracting(Issue::title)
            .contains(issueTitles)
            .doesNotContain("Open issue in concrete 0.1.1");
    }

    @Test
    void should_verify_next_milestone() throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle("0.1.2");

        String[] issueTitles = { "Closed issue in generic 0.1.x", "Closed bug in concrete 0.1.1",
                "Closed bug in generic 0.1.x", "Closed enhancement in generic 0.1.x" };
        assertThat(milestone.state()).isEqualTo("open");
        assertThat(milestone.dueOn()).isEqualTo(ReleaseDateCalculator.calculateDueDate(LocalDate.now()));
        List<Issue> issues = githubClient.getIssuesForMilestone(milestone.number());
        assertThat(issues).filteredOn(s -> "Open issue in concrete 0.1.1".equalsIgnoreCase(s.title()))
            .extracting(Issue::state)
            .containsOnly("open");
        assertThat(issues).extracting(Issue::title)
            .contains("Open issue in concrete 0.1.1")
            .doesNotContain(issueTitles);
    }

    @Test
    void should_verify_generic_milestone() throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle("0.1.x");

        List<Issue> closedIssues = githubClient.getClosedIssuesForMilestone(milestone.number());
        assertThat(closedIssues).isEmpty();
    }

    private static void runPostReleaseWorkflow() {
        log.info("Running post release action from tag");
        GithubActions.runWorkflow("post-release-workflow.yml", "v0.1.1", List.of("gh", "workflow", "run",
                "post-release-workflow.yml", "--ref", "v0.1.1", "-f", "previous_ref_name=v0.1.0"));
    }

}
