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
import io.micrometer.release.common.ReleaseDateCalculator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GithubActionsE2eTests implements GithubActions {

    @BeforeAll
    static void should_go_through_whole_flow() {
        runTrainPostReleaseWorkflow();
    }

    @Test
    void should_verify_release_notes_content() throws JsonProcessingException {
        Release release = githubClient.getRelease("v0.1.1");

        assertThat(release.body()).isEqualToIgnoringWhitespace("""
                TODO
                """);
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
            .containsOnly("Closed issue in generic " + generic, "Closed bug in concrete " + version,
                    "Closed bug in generic " + generic, "Closed enhancement in generic " + generic);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0.1.2,0.1.1
            0.2.0-RC1,0.2.0-M2
            1.0.0,1.0.0-RC1
            """)
    void should_verify_next_milestone(String next, String previous) throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle(next);

        assertThat(milestone.state()).isEqualTo("open");
        assertThat(milestone.dueOn()).isEqualTo(ReleaseDateCalculator.calculateDueDate(LocalDate.now()));
        List<Issue> issues = githubClient.getIssuesForMilestone(milestone.number());
        assertThat(issues).extracting(Issue::state).containsOnly("open");
        assertThat(issues).extracting(Issue::title).containsOnly("Open issue in concrete " + previous);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.1.x", "0.2.x", "1.0.x" })
    void should_verify_generic_milestone(String branch) throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle(branch);

        List<Issue> closedIssues = githubClient.getClosedIssuesForMilestone(milestone.number());
        assertThat(closedIssues).isEmpty();
    }

    private static void runTrainPostReleaseWorkflow() {
        log.info("Running train release from main");
        GithubActions.runWorkflow("release-train-workflow.yml",
                List.of("gh", "workflow", "run", "release-train-workflow.yml", "--ref", "main", "-f",
                        "train_versions=0.1.1,0.2.0-M2,1.0.0-RC1", "-f", "artifact_to_check=micrometer-bom"));
    }

}
