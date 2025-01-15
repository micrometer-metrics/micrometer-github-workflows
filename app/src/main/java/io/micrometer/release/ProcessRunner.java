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

class ProcessRunner {

    void run(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                .inheritIO()
                .start();
            if (process.waitFor() != 0) {
                throw new RuntimeException("Failed to run the command [" + command + "]");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("A failure around the process execution happened", e);
        }
    }
}
