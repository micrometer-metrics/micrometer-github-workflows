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

import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.micrometer.release.train.TrainOptions.Project.*;
import static org.assertj.core.api.BDDAssertions.then;

class TrainOptionsTests {

    TrainOptions trainOptions = new TrainOptions();

    @ParameterizedTest(name = "ghOrg <{0}>, contextProp <{1}>, microm <{2}>, tracing <{3}>, docsGen <{4}>")
    @MethodSource("should_parse_entries_to_project_setup_args")
    void should_parse_entries_to_project_setup(String ghOrg, String contextPropagationVersions,
            String micrometerVersions, String tracingVersions, String docsGenVersions, ProjectSetup expectedResult) {

        ProjectSetup projectSetup = trainOptions.parse(ghOrg, contextPropagationVersions, micrometerVersions,
                tracingVersions, docsGenVersions);

        then(projectSetup).isEqualTo(expectedResult);
    }

    static Stream<Arguments> should_parse_entries_to_project_setup_args() {
        return Stream.of(
                Arguments.of("micrometer-metrics/context-propagation", "1.0.0,1.1.0,1.1.1", "", "", "",
                        new ProjectSetup(List.of(contextPropagation("1.0.0"), contextPropagation("1.1.0"),
                                contextPropagation("1.1.1")), "micrometer-metrics/context-propagation")),
                Arguments.of("micrometer-metrics/micrometer", "1.0.0,1.1.0,1.1.1", "1.14.0,1.15.0,1.16.0", "", "",
                        new ProjectSetup(List.of(contextPropagation("1.0.0"), contextPropagation("1.1.0"),
                                contextPropagation("1.1.1"), micrometer("1.14.0").withContextPropagation("1.0.0"),
                                micrometer("1.15.0").withContextPropagation("1.1.0"),
                                micrometer("1.16.0").withContextPropagation("1.1.1")),
                                "micrometer-metrics/micrometer")),
                Arguments.of("micrometer-metrics/tracing", "1.0.0,1.1.0,1.1.1", "1.14.0,1.15.0,1.16.0",
                        "2.0.0,2.1.0,2.2.0", "",
                        new ProjectSetup(List.of(contextPropagation("1.0.0"), contextPropagation("1.1.0"),
                                contextPropagation("1.1.1"), micrometer("1.14.0").withContextPropagation("1.0.0"),
                                micrometer("1.15.0").withContextPropagation("1.1.0"),
                                micrometer("1.16.0").withContextPropagation("1.1.1"),
                                tracing("2.0.0").with("1.0.0", "1.14.0"), tracing("2.1.0").with("1.1.0", "1.15.0"),
                                tracing("2.2.0").with("1.1.1", "1.16.0")), "micrometer-metrics/tracing")),
                Arguments.of("micrometer-metrics/micrometer-docs-generator", "1.0.0,1.1.0,1.1.1",
                        "1.14.0,1.15.0,1.16.0", "2.0.0,2.1.0,2.2.0", "3.0.0,3.1.0,3.2.0",
                        new ProjectSetup(List.of(contextPropagation("1.0.0"), contextPropagation("1.1.0"),
                                contextPropagation("1.1.1"), micrometer("1.14.0").withContextPropagation("1.0.0"),
                                micrometer("1.15.0").withContextPropagation("1.1.0"),
                                micrometer("1.16.0").withContextPropagation("1.1.1"),
                                tracing("2.0.0").with("1.0.0", "1.14.0"), tracing("2.1.0").with("1.1.0", "1.15.0"),
                                tracing("2.2.0").with("1.1.1", "1.16.0"),
                                docsGenProject("3.0.0").with("1.14.0", "2.0.0"),
                                docsGenProject("3.1.0").with("1.15.0", "2.1.0"),
                                docsGenProject("3.2.0").with("1.16.0", "2.2.0")),
                                "micrometer-metrics/micrometer-docs-generator")),
                Arguments.of("micrometer-metrics/micrometer", "", "1.14.0,1.15.0,1.16.0", "", "",
                        new ProjectSetup(List.of(micrometer("1.14.0").noContextPropagation(),
                                micrometer("1.15.0").noContextPropagation(),
                                micrometer("1.16.0").noContextPropagation()), "micrometer-metrics/micrometer")),
                Arguments.of("micrometer-metrics/tracing", "", "1.14.0,1.15.0,1.16.0", "2.0.0,2.1.0,2.2.0", "",
                        new ProjectSetup(List.of(micrometer("1.14.0").noContextPropagation(),
                                micrometer("1.15.0").noContextPropagation(),
                                micrometer("1.16.0").noContextPropagation(), tracing("2.0.0").with(null, "1.14.0"),
                                tracing("2.1.0").with(null, "1.15.0"), tracing("2.2.0").with(null, "1.16.0")),
                                "micrometer-metrics/tracing")));
    }

    /*
     * Additional scenarios - failure: not enough versions (e.g. 1) - failure: wrong order
     * (e.g. 2,1,3)
     */

}
