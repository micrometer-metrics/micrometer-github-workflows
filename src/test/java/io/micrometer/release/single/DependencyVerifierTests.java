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

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Collections;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class DependencyVerifierTests {

    private static final String[] dependabotCreatedPrNumbers = { "gh", "pr", "list", "--search",
            "is:open author:app/dependabot created:>=2025-02-24T10:51:29Z", "--json", "number,title", "--jq",
            ".[] | select(.title | contains(\"io.micrometer\")) | .number" };

    private static final String[] dependabotPrState = { "gh", "pr", "view", "1234", "--json",
            "mergeStateStatus,mergeable,state", "--jq", "[.mergeStateStatus, .state] | join(\",\")" };

    File ghServerTimeResponse = new File(
            DependencyVerifierTests.class.getResource("/dependencyVerifier/getGhServerTime.txt").toURI());

    String orgRepo = "micrometer-metrics/micrometer";

    ProcessRunner processRunner = mock();

    DependencyVerifier verifier = new DependencyVerifier(processRunner, 1, 5, 1, TimeUnit.MILLISECONDS);

    DependencyVerifierTests() throws URISyntaxException {
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

        assertThat(verifier.verifyDependencies("micrometer-metrics/micrometer")).isTrue();

        InOrder inOrder = Mockito.inOrder(processRunner);
        inOrder.verify(processRunner).run("gh", "api", "/", "--include");
        inOrder.verify(processRunner)
            .run("gh", "api", "/repos/" + orgRepo + "/dispatches", "-X", "POST", "-F", "event_type=check-dependencies");
        inOrder.verify(processRunner).run(dependabotCreatedPrNumbers);
        inOrder.verify(processRunner).run(dependabotPrState);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_proceed_when_no_dependabot_prs_present() throws IOException {
        given(processRunner.run("gh", "api", "/", "--include"))
            .willReturn(Files.readAllLines(ghServerTimeResponse.toPath()));
        given(processRunner.run(dependabotCreatedPrNumbers)).willReturn(Collections.emptyList());

        assertThat(verifier.verifyDependencies("micrometer-metrics/micrometer")).isTrue();

        InOrder inOrder = Mockito.inOrder(processRunner);
        inOrder.verify(processRunner).run("gh", "api", "/", "--include");
        inOrder.verify(processRunner)
            .run("gh", "api", "/repos/" + orgRepo + "/dispatches", "-X", "POST", "-F", "event_type=check-dependencies");
        inOrder.verify(processRunner).run(dependabotCreatedPrNumbers);
    }

    @Test
    void should_throw_exception_when_gh_server_time_cannot_be_retrieved() {
        given(processRunner.run("gh", "api", "/", "--include")).willReturn(Collections.emptyList());

        thenThrownBy(() -> verifier.verifyDependencies("micrometer-metrics/micrometer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not get GitHub server time from response headers");
    }

    @Test
    void should_throw_exception_when_dependabot_pr_is_conflicting() throws IOException {
        given(processRunner.run("gh", "api", "/", "--include"))
            .willReturn(Files.readAllLines(ghServerTimeResponse.toPath()));
        given(processRunner.run(dependabotCreatedPrNumbers)).willReturn(Collections.singletonList("1234"));
        given(processRunner.run(dependabotPrState)).willReturn(Collections.singletonList("CONFLICTING"));

        thenThrownBy(() -> verifier.verifyDependencies("micrometer-metrics/micrometer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PR #1234 has conflicts");
    }

    @Test
    void should_throw_exception_when_timeout() throws IOException {
        given(processRunner.run("gh", "api", "/", "--include"))
            .willReturn(Files.readAllLines(ghServerTimeResponse.toPath()));
        given(processRunner.run(dependabotCreatedPrNumbers)).willReturn(Collections.singletonList("1234"));
        given(processRunner.run(dependabotPrState)).willReturn(Collections.singletonList("BLOCKED,OPEN"));

        thenThrownBy(() -> verifier.verifyDependencies("micrometer-metrics/micrometer"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Timeout waiting for Dependabot updates");
    }

    static DependencyVerifier testDependencyVerifier() {
        return new DependencyVerifier(null) {
            @Override
            boolean verifyDependencies(String orgRepository) {
                return true;
            }
        };
    }

}
