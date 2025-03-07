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

import java.util.List;

class MetaTrainGithubActionsE2eTests implements GithubActions {

    @BeforeAll
    static void should_go_through_whole_flow() {
        runTrainPostReleaseWorkflow();
    }

    TrainGithubActionsE2eTests trainGithubActionsE2eTests = new TrainGithubActionsE2eTests();

    @Test
    void should_verify_release_notes_content_for_ga() throws JsonProcessingException {
        trainGithubActionsE2eTests.should_verify_release_notes_content_for_ga();
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.1.1" })
    void should_verify_current_milestone(String version) throws JsonProcessingException {
        trainGithubActionsE2eTests.should_verify_current_milestone(version);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0.1.2,0.1.1
            """)
    void should_verify_next_milestone(String next, String previous) throws JsonProcessingException {
        trainGithubActionsE2eTests.should_verify_next_milestone(next, previous);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.1.x" })
    void should_verify_generic_milestone(String branch) throws JsonProcessingException {
        trainGithubActionsE2eTests.should_verify_generic_milestone(branch);
    }

    /**
     * Not adding context propagation versions because dependabot is going nuts about the
     * test repository.
     */
    private static void runTrainPostReleaseWorkflow() {
        log.info("Running meta train release from main");
        GithubActions.runWorkflow("meta-release-train-workflow.yml", "main", List.of("gh", "workflow", "run",
                "meta-release-train-workflow.yml", "--ref", "main", "-f", "micrometer_versions=0.1.1"));
    }

}
