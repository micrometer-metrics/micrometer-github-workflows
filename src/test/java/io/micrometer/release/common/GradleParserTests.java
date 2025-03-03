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
package io.micrometer.release.common;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GradleParserTests {

    ProcessRunner runner = mock();

    GradleParser gradleParser = new GradleParser(runner) {
        @Override
        public List<String> dependenciesLines(List<String> gradleCommand) {
            return TestGradleParser.dependenciesFromFile();
        }

        @Override
        public List<String> projectLines() {
            return TestGradleParser.projectLinesFromFile();
        }
    };

    @Test
    void should_fetch_dependencies() {
        Set<Dependency> dependencies = gradleParser.fetchAllDependencies();

        then(dependencies).hasSize(176);
        List<Dependency> micrometerDeps = dependencies.stream()
            .filter(dependency -> dependency.group().equalsIgnoreCase("io.micrometer"))
            .toList();
        then(micrometerDeps).hasSize(1);
        then(micrometerDeps.get(0)).isEqualTo(new Dependency("io.micrometer", "context-propagation", "1.1.1", false));
    }

    @Test
    void should_run_dependencies() {
        new GradleParser(runner).dependenciesLines(List.of("foo", "bar"));

        verify(runner).runSilently(List.of("foo", "bar"));
    }

    @Test
    void should_run_project_lines() {
        new GradleParser(runner).projectLines();

        verify(runner).runSilently(List.of("./gradlew", "projects"));
    }

}
