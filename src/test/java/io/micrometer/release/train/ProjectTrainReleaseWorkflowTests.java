/**
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.micrometer.release.train;

import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.single.PostReleaseWorkflow;
import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class ProjectTrainReleaseWorkflowTests {

    ReleaseScheduler releaseScheduler = mock();

    VersionToBranchConverter versionToBranchConverter = mock();

    PostReleaseTaskScheduler postReleaseTaskScheduler = mock();

    MavenCentralSyncChecker mavenCentralSyncChecker = mock();

    ProjectTrainReleaseWorkflow workflow = new ProjectTrainReleaseWorkflow(releaseScheduler, versionToBranchConverter,
            postReleaseTaskScheduler, mavenCentralSyncChecker);

    @Test
    void should_construct_instance() {
        thenNoException().isThrownBy(() -> new ProjectTrainReleaseWorkflow("foo/bar", new ProcessRunner(),
                new PostReleaseWorkflow(new ProcessRunner())));
    }

    @Test
    void should_run_train_workflow_tasks() {
        List<String> versions = List.of("1.0.0", "1.1.0");
        Map<String, String> versionToBranch = Map.of("1.0.0", "v1.0.0", "1.1.0", "main");
        given(versionToBranchConverter.convert(versions)).willReturn(versionToBranch);
        ProjectSetup projectSetup = TestProjectSetup.forMicrometer("1.0.0", "1.1.0");

        workflow.run(projectSetup);

        then(releaseScheduler).should().runReleaseAndCheckCi(versionToBranch, projectSetup);
        then(postReleaseTaskScheduler).should().runPostReleaseTasks(versions);
        then(mavenCentralSyncChecker).should().checkIfArtifactsAreInCentral(versions, projectSetup);
    }

}
