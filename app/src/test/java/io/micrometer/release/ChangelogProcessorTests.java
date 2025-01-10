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

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

class ChangelogProcessorTests {

    File input = new File(ChangelogProcessorTests.class.getResource("/processor/input.md").toURI());

    File expectedOutput = new File(ChangelogProcessorTests.class.getResource("/processor/output.md").toURI());

    File output = Files.createTempFile("output", ".md").toFile();

    ChangelogProcessor processor = new ChangelogProcessor(input, output) {
        @Override
        List<String> projectLines() throws Exception {
            URL resource = ChangelogGeneratorTests.class.getResource("/gradle/projects_output.txt");
            return Files.readAllLines(new File(resource.toURI()).toPath());
        }

        @Override
        InputStream dependenciesInputStream(List<String> gradleCommand) throws Exception {
            URL resource = ChangelogGeneratorTests.class.getResource("/gradle/dependencies_output.txt");
            return new FileInputStream(new File(resource.toURI()));
        }
    };

    ChangelogProcessorTests() throws Exception {
    }

    @Test
    void should_parse_changelog() throws Exception {
        processor.processChangelog();

        BDDAssertions.then(output).hasSameTextualContentAs(expectedOutput);
    }
}
