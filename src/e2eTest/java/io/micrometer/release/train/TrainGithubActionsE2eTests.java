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
package io.micrometer.release.train;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.release.common.GithubActions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrainGithubActionsE2eTests implements GithubActions {

    @BeforeAll
    static void should_go_through_whole_flow() {
        runTrainPostReleaseWorkflow();
    }

    @Test
    void should_verify_release_notes_content_for_ga() throws JsonProcessingException {
        Release release = githubClient.getRelease("v0.1.1");

        assertThat(release.body()).containsIgnoringWhitespaces(
                "- Closed enhancement in generic 0.1.x [#8](https://github.com/marcingrzejszczak/gh-actions-test/issues/8)",
                "- Closed bug in concrete 0.1.1 [#12](https://github.com/marcingrzejszczak/gh-actions-test/issues/12)",
                "- Closed bug in generic 0.1.x [#9](https://github.com/marcingrzejszczak/gh-actions-test/issues/9)");
    }

    @Test
    void should_verify_release_notes_content_for_M2() throws JsonProcessingException {
        Release release = githubClient.getRelease("v0.2.0-M2");

        assertThat(release.body()).containsIgnoringWhitespaces(
                "- Closed enhancement in generic 0.1.x [#8](https://github.com/marcingrzejszczak/gh-actions-test/issues/8)",
                "- Closed enhancement in generic 0.2.x [#14](https://github.com/marcingrzejszczak/gh-actions-test/issues/14)",
                "- Closed bug in concrete 0.1.1 [#12](https://github.com/marcingrzejszczak/gh-actions-test/issues/12)",
                "- Closed bug in concrete 0.2.0-M2 [#18](https://github.com/marcingrzejszczak/gh-actions-test/issues/18)",
                "- Closed bug in generic 0.1.x [#9](https://github.com/marcingrzejszczak/gh-actions-test/issues/9)",
                "- Closed bug in generic 0.2.x [#15](https://github.com/marcingrzejszczak/gh-actions-test/issues/15)");
    }

    @Test
    void should_verify_release_notes_content_for_RC1() throws JsonProcessingException {
        Release release = githubClient.getRelease("v1.0.0-RC1");

        assertThat(release.body()).containsIgnoringWhitespaces(
                "Closed enhancement in generic 0.1.x [#8](https://github.com/marcingrzejszczak/gh-actions-test/issues/8)",
                "Closed enhancement in generic 0.2.x [#14](https://github.com/marcingrzejszczak/gh-actions-test/issues/14)",
                "Closed enhancement in generic 1.0.x [#5](https://github.com/marcingrzejszczak/gh-actions-test/issues/5)",
                "Closed bug in concrete 0.1.1 [#12](https://github.com/marcingrzejszczak/gh-actions-test/issues/12)",
                "Closed bug in concrete 0.2.0-M2 [#18](https://github.com/marcingrzejszczak/gh-actions-test/issues/18)",
                "Closed bug in concrete 1.0.0-RC1 [#3](https://github.com/marcingrzejszczak/gh-actions-test/issues/3)",
                "Closed bug in generic 0.1.x [#9](https://github.com/marcingrzejszczak/gh-actions-test/issues/9)",
                "Closed bug in generic 0.2.x [#15](https://github.com/marcingrzejszczak/gh-actions-test/issues/15)",
                "Closed bug in generic 1.0.x [#4](https://github.com/marcingrzejszczak/gh-actions-test/issues/4)");
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.1.1", "0.2.0-M2", "1.0.0-RC1" })
    void should_verify_current_milestone(String version) throws JsonProcessingException {
        String generic = version.substring(0, version.lastIndexOf(".")) + ".x";
        Milestone milestone = githubClient.getMilestoneByTitle(version);

        assertThat(milestone.state()).isEqualTo("closed");
        List<Issue> issues = githubClient.getIssuesForMilestone(milestone.number());
        assertThat(issues).extracting(Issue::state).containsOnly("closed");
        assertThat(issues).extracting(Issue::title)
            .doesNotMatch(strings -> strings.stream().noneMatch(s -> s.contains("Open issue")))
            .containsOnly("Closed issue in generic " + generic, "Closed bug in concrete " + version,
                    "Closed bug in generic " + generic, "Closed enhancement in generic " + generic);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0.1.2,0.1.1
            0.2.0-M3,0.2.0-M2
            1.0.0,1.0.0-RC1
            """)
    void should_verify_next_milestone(String next, String previous) throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle(next);

        assertThat(milestone.state()).isEqualTo("open");
        assertThat(milestone.dueOn()).isEqualTo(calculateDueDate(LocalDate.now()));
        List<Issue> issues = githubClient.getIssuesForMilestone(milestone.number());
        assertThat(issues).extracting(Issue::state).containsOnly("open");
        assertThat(issues).extracting(Issue::title)
            .doesNotMatch(strings -> strings.stream().noneMatch(s -> s.contains("in generic")))
            .contains("Open issue in concrete " + previous);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.1.x", "0.2.x", "1.0.x" })
    void should_verify_generic_milestone(String branch) throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle(branch);

        List<Issue> closedIssues = githubClient.getClosedIssuesForMilestone(milestone.number());
        assertThat(closedIssues).isEmpty();
    }

    /**
     * Not adding context propagation versions because dependabot is going nuts about the
     * test repository.
     */
    private static void runTrainPostReleaseWorkflow() {
        log.info("Running train release from main");
        GithubActions.runWorkflow("release-train-workflow.yml", "main", List.of("gh", "workflow", "run",
                "release-train-workflow.yml", "--ref", "main", "-f", "micrometer_versions=0.1.1,0.2.0-M2,1.0.0-RC1"));
    }

    private static LocalDate calculateDueDate(LocalDate now) {
        // Go to first day of the next month
        LocalDate nextMonth = now.withDayOfMonth(1).plusMonths(1);

        // Find first Monday
        LocalDate firstMonday = nextMonth.plusDays((8 - nextMonth.getDayOfWeek().getValue()) % 7);

        // Add a week to get to second Monday
        return firstMonday.plusWeeks(1);
    }

}
