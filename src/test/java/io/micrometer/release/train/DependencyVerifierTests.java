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

import io.micrometer.release.common.GradleParser;
import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.common.TestGradleParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DependencyVerifierTests {

    private static final String[] dependabotCreatedPrNumbers = { "gh", "pr", "list", "--search",
            "is:open author:app/dependabot created:>=2025-02-24T10:51:29Z", "--json", "number,title", "--jq",
            ".[] | select(.title | contains(\"io.micrometer\")) | .number" };

    private static final String[] dependabotPrState = { "gh", "pr", "view", "1234", "--json",
            "mergeStateStatus,mergeable,state", "--jq", "[.mergeStateStatus, .state] | join(\",\")" };

    private static final String[] dependabotUpdateJobsIds = { "gh", "workflow", "list", "-R",
            "micrometer-metrics/micrometer", "--json", "id,name", "--jq",
            ".[] | select(.name==\"Dependabot Updates\") | .id" };

    private static final String[] dependabotUpdateJobStates = { "gh", "run", "list", "--workflow=1234", "-R",
            "micrometer-metrics/micrometer", "--created='>2025-02-24T10:51:29Z'", "--json=status", "--jq=.[].status" };

    File ghServerTimeResponse = new File(
            DependencyVerifierTests.class.getResource("/dependencyVerifier/getGhServerTime.txt").toURI());

    ProcessRunner processRunner = mock();

    DependencyVerifier verifier = new DependencyVerifier(processRunner, 1, 5, 1, TimeUnit.MILLISECONDS) {
        @Override
        GradleParser gradleParser(ProcessRunner branchProcessRunner) {
            return new TestGradleParser();
        }

        @Override
        File clonedDir(String branch) {
            try {
                return new File(DependencyVerifierTests.class.getResource("/main").toURI());
            }
            catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        String ghToken() {
            return "1234567890";
        }

        @Override
        ProcessRunner processRunnerForBranch(File clonedRepo) {
            return processRunner;
        }
    };

    DependencyVerifierTests() throws URISyntaxException {
    }

    @BeforeEach
    void setup() {
        given(processRunner.run(dependabotUpdateJobsIds)).willReturn(List.of("1234"));
        given(processRunner.run(dependabotUpdateJobStates)).willReturn(List.of("completed"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_receive_updated_dependabot_status() throws IOException {
        given(processRunner.run("gh", "api", "/", "--include"))
            .willReturn(Files.readAllLines(ghServerTimeResponse.toPath()));
        given(processRunner.run(dependabotCreatedPrNumbers)).willReturn(Collections.singletonList("1234"),
                Collections.singletonList("1234"), Collections.emptyList());
        given(processRunner.run(dependabotPrState)).willReturn(Collections.singletonList("BLOCKED,OPEN"),
                Collections.singletonList("CLOSED,MERGED"));

        verifier.verifyDependencies("main", "micrometer-metrics/micrometer");

        InOrder inOrder = Mockito.inOrder(processRunner);
        inOrder.verify(processRunner).run("gh", "api", "/", "--include");
        inOrder.verify(processRunner)
            .run("git", "remote", "set-url", "origin",
                    "https://x-access-token:1234567890@github.com/micrometer-metrics/micrometer.git");
        inOrder.verify(processRunner).run("git", "config", "user.name", "GitHub Action");
        inOrder.verify(processRunner).run("git", "config", "user.email", "action@github.com");
        inOrder.verify(processRunner).run("git", "add", ".github/dependabot.yml");
        inOrder.verify(processRunner)
            .run(eq("git"), eq("commit"), eq("-m"), matches("ci: (Add|Remove) dependabot trigger comment"));
        inOrder.verify(processRunner).run("git", "push");
        inOrder.verify(processRunner).run(dependabotCreatedPrNumbers);
        inOrder.verify(processRunner).run(dependabotPrState);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_fail_when_no_dependabot_jobs_present() throws IOException {
        given(processRunner.run("gh", "api", "/", "--include"))
            .willReturn(Files.readAllLines(ghServerTimeResponse.toPath()));
        given(processRunner.run(dependabotUpdateJobsIds)).willReturn(Collections.emptyList());

        thenThrownBy(() -> verifier.verifyDependencies("main", "micrometer-metrics/micrometer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Could not find dependabot updates");
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_fail_when_dependabot_jobs_are_not_successful() throws IOException {
        given(processRunner.run("gh", "api", "/", "--include"))
            .willReturn(Files.readAllLines(ghServerTimeResponse.toPath()));
        given(processRunner.run(dependabotUpdateJobStates)).willReturn(List.of(), List.of("BLOCKED", "OPEN"));

        thenThrownBy(() -> verifier.verifyDependencies("main", "micrometer-metrics/micrometer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Timeout waiting for Dependabot jobs to complete");
    }

    @Test
    void should_throw_exception_when_gh_server_time_cannot_be_retrieved() {
        given(processRunner.run("gh", "api", "/", "--include")).willReturn(Collections.emptyList());

        thenThrownBy(() -> verifier.verifyDependencies("main", "micrometer-metrics/micrometer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not get GitHub server time from response headers");
    }

    @Test
    void should_throw_exception_when_no_dependabot_jobs() throws IOException {
        given(processRunner.run("gh", "api", "/", "--include"))
            .willReturn(Files.readAllLines(ghServerTimeResponse.toPath()));
        given(processRunner.run(dependabotCreatedPrNumbers)).willReturn(Collections.singletonList("1234"));
        given(processRunner.run(dependabotPrState)).willReturn(Collections.singletonList("CONFLICTING"));

        thenThrownBy(() -> verifier.verifyDependencies("main", "micrometer-metrics/micrometer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PR #1234 has conflicts");
    }

    @Test
    void should_throw_exception_when_dependabot_pr_is_conflicting() throws IOException {
        given(processRunner.run("gh", "api", "/", "--include"))
            .willReturn(Files.readAllLines(ghServerTimeResponse.toPath()));
        given(processRunner.run(dependabotCreatedPrNumbers)).willReturn(Collections.singletonList("1234"));
        given(processRunner.run(dependabotPrState)).willReturn(Collections.singletonList("CONFLICTING"));

        thenThrownBy(() -> verifier.verifyDependencies("main", "micrometer-metrics/micrometer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PR #1234 has conflicts");
    }

    @Test
    void should_throw_exception_when_timeout() throws IOException {
        given(processRunner.run("gh", "api", "/", "--include"))
            .willReturn(Files.readAllLines(ghServerTimeResponse.toPath()));
        given(processRunner.run(dependabotCreatedPrNumbers)).willReturn(Collections.singletonList("1234"));
        given(processRunner.run(dependabotPrState)).willReturn(Collections.singletonList("BLOCKED,OPEN"));

        thenThrownBy(() -> verifier.verifyDependencies("main", "micrometer-metrics/micrometer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Timeout waiting for Dependabot updates");
    }

}
