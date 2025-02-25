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
package io.micrometer.release.single;

import io.micrometer.release.common.TestGradleParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.BDDAssertions.then;

class ChangelogProcessorTests {

    File input = new File(ChangelogProcessorTests.class.getResource("/processor/input.md").toURI());

    File expectedOutput = new File(ChangelogProcessorTests.class.getResource("/processor/output.md").toURI());

    File output = Files.createTempFile("output", ".md").toFile();

    ChangelogProcessor processor = testChangelogProcessor(output);

    static ChangelogProcessor testChangelogProcessor(File output) {
        return new ChangelogProcessor(output, new TestGradleParser());
    }

    ChangelogProcessorTests() throws Exception {
    }

    @Test
    void should_parse_single_changelog() throws Exception {
        processor.processChangelog(input, null);

        // Additional new line gets added
        then(Files.readString(output.toPath())).isEqualToIgnoringNewLines(Files.readString(expectedOutput.toPath()));
    }

}
