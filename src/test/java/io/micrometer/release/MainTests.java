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

import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.single.PostReleaseWorkflow;
import io.micrometer.release.train.ProjectTrainReleaseWorkflow;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class MainTests {

    ProjectTrainReleaseWorkflow projectTrainReleaseWorkflow = mock();

    PostReleaseWorkflow postReleaseWorkflow = mock();

    @Test
    void should_pick_train_when_train_env_var_set() {
        Main main = new Main() {

            @Override
            ProjectTrainReleaseWorkflow trainReleaseWorkflow(String githubOrgRepo, String artifactToCheck,
                    PostReleaseWorkflow postReleaseWorkflow, ProcessRunner processRunner) {
                return projectTrainReleaseWorkflow;
            }

            @Override
            PostReleaseWorkflow newPostReleaseWorkflow(ProcessRunner processRunner) {
                return postReleaseWorkflow;
            }

            @Override
            String getTrainVersions() {
                return "1.0.0,1.1.0";
            }
        };

        main.run();

        then(projectTrainReleaseWorkflow).should().run("1.0.0,1.1.0");
        then(postReleaseWorkflow).shouldHaveNoInteractions();
    }

    @Test
    void should_pick_single_project_post_release_when_no_train_env_var() {
        Main main = new Main() {

            @Override
            ProjectTrainReleaseWorkflow trainReleaseWorkflow(String githubOrgRepo, String artifactToCheck,
                    PostReleaseWorkflow postReleaseWorkflow, ProcessRunner processRunner) {
                return projectTrainReleaseWorkflow;
            }

            @Override
            PostReleaseWorkflow newPostReleaseWorkflow(ProcessRunner processRunner) {
                return postReleaseWorkflow;
            }

            @Override
            String getTrainVersions() {
                return "";
            }

            @Override
            String getGithubOrgRepository() {
                return "micrometer-metrics/micrometer";
            }

            @Override
            String getPreviousRefName() {
                return "v1.0.0";
            }

            @Override
            String getGithubRefName() {
                return "v1.1.0";
            }
        };

        main.run();

        then(projectTrainReleaseWorkflow).shouldHaveNoInteractions();
        then(postReleaseWorkflow).should().run("micrometer-metrics/micrometer", "v1.1.0", "v1.0.0");
    }

}
