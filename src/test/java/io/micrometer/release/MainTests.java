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
import io.micrometer.release.meta.MetaTrainReleaseWorkflow;
import io.micrometer.release.single.PostReleaseWorkflow;
import io.micrometer.release.train.ProjectTrainReleaseWorkflow;
import io.micrometer.release.train.TestProjectSetup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class MainTests {

    ProjectTrainReleaseWorkflow projectTrainReleaseWorkflow = mock();

    PostReleaseWorkflow postReleaseWorkflow = mock();

    MetaTrainReleaseWorkflow metaTrainReleaseWorkflow = mock();

    @Test
    void should_pick_train_when_train_env_var_set() {
        Main main = new Main() {

            @Override
            String getGithubOrgRepository() {
                return "micrometer-metrics/micrometer";
            }

            @Override
            ProjectTrainReleaseWorkflow trainReleaseWorkflow(PostReleaseWorkflow postReleaseWorkflow,
                    ProcessRunner processRunner) {
                return projectTrainReleaseWorkflow;
            }

            @Override
            PostReleaseWorkflow newPostReleaseWorkflow(ProcessRunner processRunner) {
                return postReleaseWorkflow;
            }

            @Override
            MetaTrainReleaseWorkflow metaReleaseWorkflow(PostReleaseWorkflow postReleaseWorkflow) {
                return metaTrainReleaseWorkflow;
            }

            @Override
            String getMicrometerVersions() {
                return "1.0.0,1.1.0,1.2.0";
            }
        };

        main.run();

        then(projectTrainReleaseWorkflow).should().run(TestProjectSetup.forMicrometer("1.0.0", "1.1.0", "1.2.0"));
        then(postReleaseWorkflow).shouldHaveNoInteractions();
        then(metaTrainReleaseWorkflow).shouldHaveNoInteractions();
    }

    @Test
    void should_pick_single_project_post_release_when_no_train_env_var() {
        Main main = new Main() {

            @Override
            ProjectTrainReleaseWorkflow trainReleaseWorkflow(PostReleaseWorkflow postReleaseWorkflow,
                    ProcessRunner processRunner) {
                return projectTrainReleaseWorkflow;
            }

            @Override
            PostReleaseWorkflow newPostReleaseWorkflow(ProcessRunner processRunner) {
                return postReleaseWorkflow;
            }

            @Override
            MetaTrainReleaseWorkflow metaReleaseWorkflow(PostReleaseWorkflow postReleaseWorkflow) {
                return metaTrainReleaseWorkflow;
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
        then(metaTrainReleaseWorkflow).shouldHaveNoInteractions();
        then(postReleaseWorkflow).should().run("v1.1.0", "v1.0.0");
    }

    @Test
    void should_pick_meta_release_when_meta_release_enabled_set() {
        Main main = new Main() {

            @Override
            ProjectTrainReleaseWorkflow trainReleaseWorkflow(PostReleaseWorkflow postReleaseWorkflow,
                    ProcessRunner processRunner) {
                return projectTrainReleaseWorkflow;
            }

            @Override
            PostReleaseWorkflow newPostReleaseWorkflow(ProcessRunner processRunner) {
                return postReleaseWorkflow;
            }

            @Override
            MetaTrainReleaseWorkflow metaReleaseWorkflow(PostReleaseWorkflow postReleaseWorkflow) {
                return metaTrainReleaseWorkflow;
            }

            @Override
            String getGithubOrgRepository() {
                return "micrometer-metrics/micrometer";
            }

            @Override
            String getMetaReleaseEnabled() {
                return "true";
            }

            @Override
            String getMicrometerVersions() {
                return "1.0.0,1.1.0,1.2.0";
            }
        };

        main.run();

        then(postReleaseWorkflow).shouldHaveNoInteractions();
        then(metaTrainReleaseWorkflow).should().run(List.of(TestProjectSetup.forMicrometer("1.0.0", "1.1.0", "1.2.0")));
    }

}
