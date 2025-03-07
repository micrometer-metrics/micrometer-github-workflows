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
package io.micrometer.release.meta;

import io.micrometer.release.single.PostReleaseWorkflow;
import io.micrometer.release.train.ProjectTrainReleaseWorkflow;
import io.micrometer.release.train.TestProjectSetup;
import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.File;
import java.util.List;

import static org.mockito.Mockito.mock;

class MetaTrainReleaseWorkflowTests {

    PostReleaseWorkflow postReleaseWorkflow = mock();

    ProjectTrainReleaseWorkflow projectTrainReleaseWorkflow = mock();

    MetaTrainReleaseWorkflow workflow = new MetaTrainReleaseWorkflow(postReleaseWorkflow) {
        @Override
        ProjectTrainReleaseWorkflow workflow(ProjectSetup projectSetup, File projectSubfolder) {
            return projectTrainReleaseWorkflow;
        }
    };

    @Test
    void should_run_meta_release_in_order() {
        ProjectSetup micrometerSetup = TestProjectSetup.forMicrometer("1.0.0");
        ProjectSetup tracingSetup = TestProjectSetup.forTracing("2.0.0");

        workflow.run(List.of(micrometerSetup, tracingSetup));

        InOrder inOrder = Mockito.inOrder(projectTrainReleaseWorkflow);
        inOrder.verify(projectTrainReleaseWorkflow).run(micrometerSetup);
        inOrder.verify(projectTrainReleaseWorkflow).run(tracingSetup);
    }

}
