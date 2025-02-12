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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GH_TOKEN", matches = ".*\\S.*")
class GithubActionsAcceptanceTests {

    private static final Logger log = LoggerFactory.getLogger(GithubActionsAcceptanceTests.class);

    private static final String REPO = "marcingrzejszczak/gh-actions-test";

    private static final GithubClient githubClient = new GithubClient(System.getenv("GH_TOKEN"), REPO);

    private static final ProcessRunner processRunner = new ProcessRunner(REPO);

    @BeforeAll
    static void should_go_through_whole_flow() throws InterruptedException {
        log.info(
                "This test requires GH connection and will operate on [{}] repository. It's quite slow because it runs GH actions so please be patient...",
                REPO);
        resetMilestones();
        runPostReleaseWorkflow();
    }

    @Test
    void should_verify_release_notes_content() throws JsonProcessingException {
        Release release = githubClient.getRelease("v0.0.2");

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
    void should_verify_milestone_0_0_2() throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle("0.0.2");

        assertThat(milestone.state()).isEqualTo("closed");
        List<Issue> issues = githubClient.getIssuesForMilestone(milestone.number());
        assertThat(issues).extracting(Issue::state).containsOnly("closed");
        assertThat(issues).extracting(Issue::title)
            .containsOnly("Closed issue in generic", "Closed bug in concrete", "Closed bug in generic",
                    "Closed enhancement in generic");
    }

    @Test
    void should_verify_milestone_0_0_3() throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle("0.0.3");

        assertThat(milestone.state()).isEqualTo("open");
        assertThat(milestone.dueOn()).isEqualTo(ReleaseDateCalculator.calculateDueDate(LocalDate.now()));
        List<Issue> issues = githubClient.getIssuesForMilestone(milestone.number());
        assertThat(issues).extracting(Issue::state).containsOnly("open");
        assertThat(issues).extracting(Issue::title).containsOnly("Open issue in concrete");
    }

    @Test
    void should_verify_milestone_0_0_x() throws JsonProcessingException {
        Milestone milestone = githubClient.getMilestoneByTitle("0.0.x");

        List<Issue> closedIssues = githubClient.getClosedIssuesForMilestone(milestone.number());
        assertThat(closedIssues).isEmpty();
    }

    private static void resetMilestones() throws InterruptedException {
        log.info("Resetting repository state");
        processRunner.run("gh", "workflow", "run", "reset-milestones.yml");
        waitForWorkflowCompletion("reset-milestones.yml");
    }

    private static void runPostReleaseWorkflow() throws InterruptedException {
        log.info("Running post release action from tag");
        processRunner.run("gh", "workflow", "run", "post-release-workflow.yml", "--ref", "v0.0.2", "-f",
                "previous_ref_name=v0.0.1");
        waitForWorkflowCompletion("post-release-workflow.yml");
    }

    private static void waitForWorkflowCompletion(String workflowFile) throws InterruptedException {
        log.info("Waiting for workflow [{}] scheduling...", workflowFile);
        Thread.sleep(10_000); // Wait for the action to schedule
        log.info("Waiting for workflow [{}] completion...", workflowFile);
        boolean completed = false;
        int maxAttempts = 30;
        int attempts = 0;
        while (!completed && attempts < maxAttempts) { // 5 minute timeout
            List<String> status = processRunner.run("gh", "run", "list", "--workflow", workflowFile, "--limit", "1");
            log.info("Workflow [{}] not completed yet - attempt [{}]/[{}]", workflowFile, attempts + 1, maxAttempts);
            completed = status.stream().anyMatch(line -> line.contains("completed"));
            if (!completed) {
                Thread.sleep(10_000);
                attempts++;
            }
        }
        if (!completed) {
            throw new RuntimeException("Workflow " + workflowFile + " did not complete within timeout");
        }
        log.info("Workflow [{}] completed successfully!", workflowFile);
    }

    record Release(String body) {

    }

    record Milestone(int number, String state, String title, LocalDate dueOn) {

    }

    record Issue(int number, String state, String title) {

    }

    static class GithubClient {

        private final String repo;

        private final ProcessRunner processRunner;

        private final ObjectMapper objectMapper;

        GithubClient(String token, String repo) {
            this.repo = repo;
            this.processRunner = new ProcessRunner(repo).withEnvVars(
                    Map.of("JAVA_HOME", JavaHomeFinder.findJavaHomePath(), "GH_TOKEN", token != null ? token : ""));
            this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        Release getRelease(String tag) throws JsonProcessingException {
            List<String> output = processRunner.run("gh", "api", "/repos/" + repo + "/releases/tags/" + tag);
            return parseReleaseFromJson(output.get(0));
        }

        Milestone getMilestoneByTitle(String title) throws JsonProcessingException {
            List<String> output = processRunner.run("gh", "api", "/repos/" + repo + "/milestones");
            return parseMilestoneFromJson(output.get(0), title);
        }

        List<Issue> getIssuesForMilestone(int milestoneNumber) throws JsonProcessingException {
            List<String> output = processRunner.run("gh", "api",
                    "/repos/" + repo + "/issues?milestone=" + milestoneNumber);
            return parseIssuesFromJson(output.get(0));
        }

        List<Issue> getClosedIssuesForMilestone(int milestoneNumber) throws JsonProcessingException {
            List<String> output = processRunner.run("gh", "api",
                    "/repos/" + repo + "/issues?milestone=" + milestoneNumber + "&state=closed");
            return parseIssuesFromJson(output.get(0));
        }

        private Release parseReleaseFromJson(String json) throws JsonProcessingException {
            JsonNode root = objectMapper.readTree(json);
            return new Release(root.get("body").asText());
        }

        private Milestone parseMilestoneFromJson(String json, String title) throws JsonProcessingException {
            JsonNode root = objectMapper.readTree(json);
            for (JsonNode milestone : root) {
                if (milestone.get("title").asText().equals(title)) {
                    return new Milestone(milestone.get("number").asInt(), milestone.get("state").asText(),
                            milestone.get("title").asText(),
                            milestone.get("due_on") != null && !milestone.get("due_on").isNull()
                                    ? LocalDate.parse(milestone.get("due_on").asText().substring(0, 10)) : null);
                }
            }
            throw new RuntimeException("Milestone with title " + title + " not found");
        }

        private List<Issue> parseIssuesFromJson(String json) throws JsonProcessingException {
            JsonNode root = objectMapper.readTree(json);
            List<Issue> issues = new ArrayList<>();
            for (JsonNode issue : root) {
                issues.add(new Issue(issue.get("number").asInt(), issue.get("state").asText(),
                        issue.get("title").asText()));
            }
            return issues;
        }

    }

}
