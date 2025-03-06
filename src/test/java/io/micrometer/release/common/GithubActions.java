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
package io.micrometer.release.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.release.JavaHomeFinder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("e2e")
public interface GithubActions {

    Logger log = LoggerFactory.getLogger(GithubActions.class);

    String REPO = "marcingrzejszczak/gh-actions-test";

    GithubClient githubClient = new GithubClient(System.getenv("GH_TOKEN"), REPO);

    ProcessRunner processRunner = new ProcessRunner(REPO);

    @BeforeAll
    static void resetsMilestones() throws InterruptedException {
        assertThat(System.getenv("GH_TOKEN")).as("GH_TOKEN env var must be set!").isNotBlank();
        log.info(
                "This test requires GH connection and will operate on [{}] repository. It's quite slow because it runs GH actions so please be patient...",
                REPO);
        resetMilestones();
    }

    private static void resetMilestones() throws InterruptedException {
        log.info("Resetting repository state");
        runAndWaitResetMiestones("0.1.x");
        runAndWaitResetMiestones("0.2.x");
        runAndWaitResetMiestones("main");
    }

    private static void runAndWaitResetMiestones(String branch) throws InterruptedException {
        log.info("Resetting branch {}", branch);
        processRunner.run("gh", "workflow", "run", "reset-milestones.yml", "-r", branch);
        waitForWorkflowCompletion("reset-milestones.yml", branch);
    }

    static void runWorkflow(String workflowName, String branch, List<String> commands) {
        log.info("Running workflow with name [{}]", workflowName);
        processRunner.run(commands);
        try {
            waitForWorkflowCompletion(workflowName, branch);
        }
        catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void waitForWorkflowCompletion(String workflowFile, String branch) throws InterruptedException {
        log.info("Waiting for workflow [{}] scheduling...", workflowFile);
        Thread.sleep(10_000); // Wait for the action to schedule
        log.info("Waiting for workflow [{}] completion...", workflowFile);
        boolean completed = false;
        int maxAttempts = 30;
        int attempts = 0;
        while (!completed && attempts < maxAttempts) { // 5 minute timeout
            List<String> status = processRunner.run("gh", "run", "list", "--workflow", workflowFile, "--branch", branch,
                    "--limit", "1");
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

    class GithubClient {

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

        public Release getRelease(String tag) throws JsonProcessingException {
            String output = String.join("\n",
                    processRunner.run("gh", "api", "--paginate", "/repos/" + repo + "/releases/tags/" + tag));
            return parseReleaseFromJson(output);
        }

        public void createReleaseAndTag(String tagName) {
            log.info("Creating tag and release [{}]", tagName);

            processRunner.run("gh", "release", "create", tagName, "--title", tagName.replace("v", ""), "--notes",
                    "Release " + tagName, "--target", "main");

            log.info("Successfully created tag and release [{}]", tagName);
        }

        public Milestone getMilestoneByTitle(String title) throws JsonProcessingException {
            String output = String.join("\n",
                    processRunner.run("gh", "api", "--paginate", "/repos/" + repo + "/milestones?state=all"));
            return parseMilestoneFromJson(output, title);
        }

        public List<Issue> getIssuesForMilestone(int milestoneNumber) throws JsonProcessingException {
            String output = String.join("\n", processRunner.run("gh", "api", "--paginate",
                    "/repos/" + repo + "/issues?milestone=" + milestoneNumber + "&state=all"));
            return parseIssuesFromJson(output);
        }

        public List<Issue> getClosedIssuesForMilestone(int milestoneNumber) throws JsonProcessingException {
            String output = String.join("\n", processRunner.run("gh", "api", "--paginate",
                    "/repos/" + repo + "/issues?milestone=" + milestoneNumber + "&state=closed"));
            return parseIssuesFromJson(output);
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
