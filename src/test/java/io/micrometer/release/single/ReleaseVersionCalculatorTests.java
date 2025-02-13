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

import static org.assertj.core.api.BDDAssertions.then;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReleaseVersionCalculatorTests {

    @ParameterizedTest(name = "{index} For current version <{0}> next version should be <{1}>")
    @CsvSource(textBlock = """
            1.0.0-M1, 1.0.0-M2
            1.0.0-M2, 1.0.0-M3
            1.0.0-M3, 1.0.0-RC1
            1.0.0-RC1, 1.0.0
            1.0.0, 1.0.1
            """)
    void should_calculate_next_version(String title, String expectedVersion) {
        String nextVersion = ReleaseVersionCalculator.calculateNextVersion(title);

        then(nextVersion).isEqualTo(expectedVersion);
    }

}
