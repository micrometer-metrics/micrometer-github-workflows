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

import io.micrometer.release.train.TrainOptions.Project;
import io.micrometer.release.train.TrainOptions.ProjectSetup;
import io.micrometer.release.train.TrainOptions.ProjectWithDependencies;

import java.util.Arrays;
import java.util.List;

public class TestProjectSetup {

    public static ProjectSetup forMicrometer(String... projectVersions) {
        List<? extends ProjectWithDependencies> list = Arrays.stream(projectVersions)
            .map(s -> Project.micrometer(s).noContextPropagation())
            .toList();
        return new ProjectSetup((List<ProjectWithDependencies>) list, "micrometer-metrics/micrometer");
    }

    public static ProjectSetup forTracing(String... projectVersions) {
        List<? extends ProjectWithDependencies> list = Arrays.stream(projectVersions)
            .map(s -> Project.tracing(s).with(null, "1.0.0"))
            .toList();
        return new ProjectSetup((List<ProjectWithDependencies>) list, "micrometer-metrics/tracing");
    }

}
