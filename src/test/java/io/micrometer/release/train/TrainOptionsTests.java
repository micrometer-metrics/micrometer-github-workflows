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

import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.micrometer.release.train.TrainOptions.Project.*;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class TrainOptionsTests {

    TrainOptions trainOptions = TrainOptions.withTestMode(false);

    @Test
    void should_throw_exception_for_no_versions() {
        thenThrownBy(() -> trainOptions.parseForSingleProjectTrain("foo", "", null, "", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("At least one of the versions must be set...");
    }

    @ParameterizedTest(name = "contextProp <{0}>, microm <{1}>, tracing <{2}>, docsGen <{3}>")
    @CsvSource(textBlock = """
            1;2,3;4,5,6;7,8,9,0
            '';1,2;3,4,5;6,7,8,9
            """, delimiter = ';')
    void should_throw_exception_for_versions_with_wrong_number_of_arguments(String contextPropagationVersions,
            String micrometerVersions, String tracingVersions, String docsGenVersions) {
        thenThrownBy(() -> trainOptions.parseForSingleProjectTrain("foo", contextPropagationVersions,
                micrometerVersions, tracingVersions, docsGenVersions))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Project versions need to be of the same size or be empty. Provided setup:")
            .hasMessageContaining("[Context Propagation] versions size [%s], values [%s]"
                .formatted(actualSize(contextPropagationVersions), contextPropagationVersions))
            .hasMessageContaining("[Micrometer] versions size [%s], values [%s]"
                .formatted(actualSize(micrometerVersions), micrometerVersions))
            .hasMessageContaining(
                    "[Tracing] versions size [%s], values [%s]".formatted(actualSize(tracingVersions), tracingVersions))
            .hasMessageContaining("[Docs Gen] versions size [%s], values [%s]".formatted(actualSize(docsGenVersions),
                    docsGenVersions));
    }

    private int actualSize(String text) {
        return text.isBlank() ? 0 : text.split(",").length;
    }

    @ParameterizedTest(name = "ghOrg <{0}>, contextProp <{1}>, microm <{2}>, tracing <{3}>, docsGen <{4}>")
    @MethodSource("should_parse_for_single_project_train_entries_to_project_setup_args")
    void should_parse_for_single_project_train_entries_to_project_setup(String ghOrg, String contextPropagationVersions,
            String micrometerVersions, String tracingVersions, String docsGenVersions, ProjectSetup expectedResult) {

        ProjectSetup projectSetup = trainOptions.parseForSingleProjectTrain(ghOrg, contextPropagationVersions,
                micrometerVersions, tracingVersions, docsGenVersions);

        then(projectSetup).isEqualTo(expectedResult);
    }

    static Stream<Arguments> should_parse_for_single_project_train_entries_to_project_setup_args() {
        return Stream.of(
                Arguments.of("micrometer-metrics/context-propagation", "1.0.0,1.1.0,1.1.1", "", "", "",
                        singleContextPropagation("micrometer-metrics/context-propagation")),
                Arguments.of("micrometer-metrics/micrometer", "1.0.0,1.1.0,1.1.1", "1.14.0,1.15.0,1.16.0", "", "",
                        contextAndMicrometer("micrometer-metrics/micrometer")),
                Arguments.of("micrometer-metrics/tracing", "1.0.0,1.1.0,1.1.1", "1.14.0,1.15.0,1.16.0",
                        "2.0.0,2.1.0,2.2.0", "", contextMicrometerAndTracing("micrometer-metrics/tracing")),
                Arguments.of("micrometer-metrics/micrometer-docs-generator", "1.0.0,1.1.0,1.1.1",
                        "1.14.0,1.15.0,1.16.0", "2.0.0,2.1.0,2.2.0", "3.0.0,3.1.0,3.2.0",
                        allFourProjects("micrometer-metrics/micrometer-docs-generator")),
                Arguments.of("micrometer-metrics/micrometer", "", "1.14.0,1.15.0,1.16.0", "", "", justMicrometer()),
                Arguments.of("micrometer-metrics/tracing", "", "1.14.0,1.15.0,1.16.0", "2.0.0,2.1.0,2.2.0", "",
                        micrometerAndTracing("micrometer-metrics/tracing")));
    }

    private static ProjectSetup micrometerAndTracing(String orgRepo) {
        return new ProjectSetup(
                List.of(micrometer("1.14.0").noContextPropagation(), micrometer("1.15.0").noContextPropagation(),
                        micrometer("1.16.0").noContextPropagation(), tracing("2.0.0").with(null, "1.14.0"),
                        tracing("2.1.0").with(null, "1.15.0"), tracing("2.2.0").with(null, "1.16.0")),
                orgRepo);
    }

    private static ProjectSetup contextAndMicrometer(String orgRepo) {
        return new ProjectSetup(List.of(contextPropagation("1.0.0"), contextPropagation("1.1.0"),
                contextPropagation("1.1.1"), micrometer("1.14.0").withContextPropagation("1.0.0"),
                micrometer("1.15.0").withContextPropagation("1.1.0"),
                micrometer("1.16.0").withContextPropagation("1.1.1")), orgRepo);
    }

    @ParameterizedTest(name = "contextProp <{0}>, microm <{1}>, tracing <{2}>, docsGen <{3}>")
    @MethodSource("should_parse_for_meta_train_entries_to_project_setup_args")
    void should_parse_for_meta_train_entries_to_project_setup(String contextPropagationVersions,
            String micrometerVersions, String tracingVersions, String docsGenVersions,
            List<ProjectSetup> expectedResult) {

        List<ProjectSetup> projectSetups = trainOptions.parseForMetaTrain(contextPropagationVersions,
                micrometerVersions, tracingVersions, docsGenVersions);

        then(projectSetups).isEqualTo(expectedResult);
    }

    static Stream<Arguments> should_parse_for_meta_train_entries_to_project_setup_args() {
        return Stream.of(
                // Arguments.of("1.0.0,1.1.0,1.1.1", "", "", "",
                // List.of(singleContextPropagation("micrometer-metrics/context-propagation"))),
                Arguments.of("1.0.0,1.1.0,1.1.1", "1.14.0,1.15.0,1.16.0", "", "",
                        List.of(contextAndMicrometer("micrometer-metrics/context-propagation"),
                                contextAndMicrometer("micrometer-metrics/micrometer"))),
                Arguments.of("1.0.0,1.1.0,1.1.1", "1.14.0,1.15.0,1.16.0", "2.0.0,2.1.0,2.2.0", "",
                        List.of(contextMicrometerAndTracing("micrometer-metrics/context-propagation"),
                                contextMicrometerAndTracing("micrometer-metrics/micrometer"),
                                contextMicrometerAndTracing("micrometer-metrics/tracing"))),
                Arguments.of("1.0.0,1.1.0,1.1.1", "1.14.0,1.15.0,1.16.0", "2.0.0,2.1.0,2.2.0", "3.0.0,3.1.0,3.2.0",
                        List.of(allFourProjects("micrometer-metrics/context-propagation"),
                                allFourProjects("micrometer-metrics/micrometer"),
                                allFourProjects("micrometer-metrics/tracing"),
                                allFourProjects("micrometer-metrics/micrometer-docs-generator"))),
                Arguments.of("", "1.14.0,1.15.0,1.16.0", "", "", List.of(justMicrometer())),
                Arguments.of("", "1.14.0,1.15.0,1.16.0", "2.0.0,2.1.0,2.2.0", "",
                        List.of(micrometerAndTracing("micrometer-metrics/micrometer"),
                                micrometerAndTracing("micrometer-metrics/tracing"))));
    }

    private static ProjectSetup justMicrometer() {
        return new ProjectSetup(List.of(micrometer("1.14.0").noContextPropagation(),
                micrometer("1.15.0").noContextPropagation(), micrometer("1.16.0").noContextPropagation()),
                "micrometer-metrics/micrometer");
    }

    private static ProjectSetup allFourProjects(String orgRepo) {
        return new ProjectSetup(List.of(contextPropagation("1.0.0"), contextPropagation("1.1.0"),
                contextPropagation("1.1.1"), micrometer("1.14.0").withContextPropagation("1.0.0"),
                micrometer("1.15.0").withContextPropagation("1.1.0"),
                micrometer("1.16.0").withContextPropagation("1.1.1"), tracing("2.0.0").with("1.0.0", "1.14.0"),
                tracing("2.1.0").with("1.1.0", "1.15.0"), tracing("2.2.0").with("1.1.1", "1.16.0"),
                docsGenProject("3.0.0").with("1.14.0", "2.0.0"), docsGenProject("3.1.0").with("1.15.0", "2.1.0"),
                docsGenProject("3.2.0").with("1.16.0", "2.2.0")), orgRepo);
    }

    private static ProjectSetup contextMicrometerAndTracing(String orgRepo) {
        return new ProjectSetup(List.of(contextPropagation("1.0.0"), contextPropagation("1.1.0"),
                contextPropagation("1.1.1"), micrometer("1.14.0").withContextPropagation("1.0.0"),
                micrometer("1.15.0").withContextPropagation("1.1.0"),
                micrometer("1.16.0").withContextPropagation("1.1.1"), tracing("2.0.0").with("1.0.0", "1.14.0"),
                tracing("2.1.0").with("1.1.0", "1.15.0"), tracing("2.2.0").with("1.1.1", "1.16.0")), orgRepo);
    }

    private static ProjectSetup singleContextPropagation(String orgRepo) {
        return new ProjectSetup(
                List.of(contextPropagation("1.0.0"), contextPropagation("1.1.0"), contextPropagation("1.1.1")),
                orgRepo);
    }

    /*
     * Additional scenarios - failure: not enough versions (e.g. 1) - failure: wrong order
     * (e.g. 2,1,3)
     */

}
