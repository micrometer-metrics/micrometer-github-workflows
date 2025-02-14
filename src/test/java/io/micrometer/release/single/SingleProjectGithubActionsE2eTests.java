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
import io.micrometer.release.common.ReleaseDateCalculator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SingleProjectGithubActionsE2eTests implements GithubActions {

    @BeforeAll
    static void should_go_through_whole_flow() {
        runPostReleaseWorkflow();
    }

    @Test
    void should_verify_release_notes_content() throws JsonProcessingException {
        Release release = githubClient.getRelease("v0.1.1");

        assertThat(release.body()).isEqualToIgnoringWhitespace(
                """
                        ## :star: New Features

                        - Closed enhancement in generic [#5](https://github.com/marcingrzejszczak/gh-actions-test/issues/5)
                        - Foo [#2](https://github.com/micrometer-metrics/build-test/issues/2)

                        ## :lady_beetle: Bug Fixes

                        - Closed bug in concrete [#3](https://github.com/marcingrzejszczak/gh-actions-test/issues/3)
                        - Closed bug in generic [#4](https://github.com/marcingrzejszczak/gh-actions-test/issues/4)
                        - Foo2 [#53](https://github.com/micrometer-metrics/build-test/issues/53)

                        ## :hammer: Dependency Upgrades

                        - Bump com.fasterxml.jackson.core:jackson-databind from 2.17.1 to 2.18.2 [#50](https://github.com/micrometer-metrics/build-test/pull/50)
                        - Bump com.google.cloud:google-cloud-monitoring from 3.47.0 to 3.56.0 [#51](https://github.com/micrometer-metrics/build-test/pull/51)
                        - Bump io.micrometer:context-propagation from 1.1.1 to 1.1.2 [#28](https://github.com/micrometer-metrics/build-test/pull/28)
                        - Bump io.projectreactor:reactor-bom from 2022.0.20 to 2022.0.22 [#49](https://github.com/micrometer-metrics/build-test/pull/49)
                        - Bump io.spring.develocity.conventions from 0.0.19 to 0.0.22 [#30](https://github.com/micrometer-metrics/build-test/pull/30)
                        - Bump jakarta.jms:jakarta.jms-api from 3.0.0 to 3.1.0 [#43](https://github.com/micrometer-metrics/build-test/pull/43)
                        - Bump maven-resolver from 1.9.20 to 1.9.22 [#32](https://github.com/micrometer-metrics/build-test/pull/32)
                        - Bump me.champeau.gradle:japicmp-gradle-plugin from 0.4.3 to 0.4.5 [#52](https://github.com/micrometer-metrics/build-test/pull/52)
                        """);
    }

    @Test
    void should_verify_current_milestone() throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle("0.1.1");

        assertThat(milestone.state()).isEqualTo("closed");
        List<Issue> issues = githubClient.getIssuesForMilestone(milestone.number());
        assertThat(issues).extracting(Issue::state).containsOnly("closed");
        assertThat(issues).extracting(Issue::title)
            .containsOnly("Closed issue in generic", "Closed bug in concrete", "Closed bug in generic",
                    "Closed enhancement in generic");
    }

    @Test
    void should_verify_next_milestone() throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle("0.1.2");

        assertThat(milestone.state()).isEqualTo("open");
        assertThat(milestone.dueOn()).isEqualTo(ReleaseDateCalculator.calculateDueDate(LocalDate.now()));
        List<Issue> issues = githubClient.getIssuesForMilestone(milestone.number());
        assertThat(issues).extracting(Issue::state).containsOnly("open");
        assertThat(issues).extracting(Issue::title).containsOnly("Open issue in concrete");
    }

    @Test
    void should_verify_generic_milestone() throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle("0.1.x");

        List<Issue> closedIssues = githubClient.getClosedIssuesForMilestone(milestone.number());
        assertThat(closedIssues).isEmpty();
    }

    private static void runPostReleaseWorkflow() {
        log.info("Running post release action from tag");
        GithubActions.runWorkflow("post-release-workflow.yml", List.of("gh", "workflow", "run",
                "post-release-workflow.yml", "--ref", "v0.1.1", "-f", "previous_ref_name=v0.1.0"));
    }

}
