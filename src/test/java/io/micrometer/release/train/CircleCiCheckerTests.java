/**
 * Copyright 2025 the original author or authors.
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.Builder;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class CircleCiCheckerTests {

    @RegisterExtension
    static WireMockExtension wm1 = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Test
    void should_return_true_for_all_successful_workflow_steps() throws IOException, InterruptedException {
        CircleCiChecker checker = getChecker("success", wm1.url("/api/v2/"));

        then(checker.checkBuildStatus("1.14.9")).as("All workflows steps were successful").isTrue();
    }

    @Test
    void should_return_false_when_not_failing_but_not_successful_workflow_steps()
            throws IOException, InterruptedException {
        CircleCiChecker checker = getChecker("in-progress", wm1.url("/api/v2/"));

        then(checker.checkBuildStatus("1.14.9"))
            .as("At least one workflow step was not successful, however it wasn't failing")
            .isFalse();
    }

    @Test
    void should_throw_exception_when_no_matching_tag() {
        CircleCiChecker checker = getChecker("success", wm1.url("/api/v2/"));

        thenThrownBy(() -> checker.checkBuildStatus("1.0.0-notFound"))
            .hasMessageContaining("No CircleCI pipeline found for tag [v1.0.0-notFound]")
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_throw_exception_when_at_least_one_workflow_step_failed() {
        CircleCiChecker checker = getChecker("failed", wm1.url("/api/v2/"));

        thenThrownBy(() -> checker.checkBuildStatus("1.14.9")).as("At least one workflow step should be failing")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Workflow [deploy] failed!");
    }

    static CircleCiChecker getChecker(String status, String url) {
        return new CircleCiChecker("foo", "micrometer-metrics/micrometer", HttpClient.newBuilder().build(),
                new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false), url, 1, 1) {
            @Override
            Builder requestBuilder(String workflowUrl) {
                return super.requestBuilder(workflowUrl).header("Test-Status", status);
            }
        };
    }

}
