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

import java.io.IOException;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.BDDAssertions.then;

class ProcessRunnerTests {

    @Test
    void should_run_process() throws IOException {
        File tempFile = Files.createTempFile("micrometer-release", ".txt").toFile();
        tempFile.delete();
        then(tempFile).doesNotExist();

        new ProcessRunner().run("touch", tempFile.getAbsolutePath());

        then(tempFile).exists();
    }

}
