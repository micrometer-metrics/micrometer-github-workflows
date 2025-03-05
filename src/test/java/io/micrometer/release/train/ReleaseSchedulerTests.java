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
import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class ReleaseSchedulerTests {

    CircleCiChecker checker = mock();

    ProcessRunner processRunner = mock();

    DependencyVerifier dependencyVerifier = mock();

    ReleaseScheduler releaseScheduler = new ReleaseScheduler(checker, processRunner, dependencyVerifier);

    @Test
    void should_schedule_releases() throws IOException, InterruptedException {
        given(checker.checkBuildStatus(BDDMockito.anyString())).willReturn(true);

        releaseScheduler.runReleaseAndCheckCi(Map.of("1.0.0", "v1.0.0", "2.0.0", "v2.0.0"),
                TestProjectSetup.forMicrometer("1.0.0", "2.0.0"));

        then(processRunner).should().run("gh", "release", "create", "v1.0.0", "--target", "v1.0.0", "-t", "1.0.0");
        then(processRunner).should().run("gh", "release", "create", "v2.0.0", "--target", "v2.0.0", "-t", "2.0.0");

        then(checker).should().checkBuildStatus("1.0.0");
        then(checker).should().checkBuildStatus("2.0.0");
    }

    @Test
    void should_not_make_a_release_when_dependency_check_fails() {
        ReleaseScheduler releaseScheduler = new ReleaseScheduler(checker, processRunner,
                new DependencyVerifier(processRunner, ProjectTrainReleaseWorkflow.OBJECT_MAPPER) {
                    @Override
                    void verifyDependencies(String branch, String orgRepository, ProjectSetup projectSetup) {
                        throw new IllegalStateException("BOOM!"); // mock doesn't work for
                        // some reason
                    }
                });

        thenThrownBy(() -> releaseScheduler.runReleaseAndCheckCi(Map.of("1.0.0", "v1.0.0"),
                TestProjectSetup.forMicrometer("1.0.0")))
            .isInstanceOf(CompletionException.class)
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("BOOM!");

        then(processRunner).shouldHaveNoInteractions();
        then(checker).shouldHaveNoInteractions();
    }

    @Test
    void should_throw_exception_when_build_status_not_successful() throws IOException, InterruptedException {
        given(checker.checkBuildStatus(BDDMockito.anyString())).willReturn(false);

        thenThrownBy(() -> releaseScheduler.runReleaseAndCheckCi(Map.of("1.0.0", "v1.0.0", "2.0.0", "v2.0.0"),
                TestProjectSetup.forMicrometer("1.0.0", "2.0.0")))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Build failed for version:");
    }

}
