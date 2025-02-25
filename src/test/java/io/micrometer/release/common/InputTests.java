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
package io.micrometer.release.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.BDDAssertions.thenThrownBy;

class InputTests {

    @ParameterizedTest
    @CsvSource(textBlock = """
            ,v1.2.3,,No repo found, please provide the GITHUB_REPOSITORY env variable
            foo/bar,,,No github ref found, please provide the GITHUB_REF_NAME env variable
            foo/bar,1.2.3,,Github ref must be a tag (must start with 'v'): 1.2.3
            foo/bar,v1.2.3,2.3.4,Previous github ref must be a tag (must start with 'v'): 2.3.4
            """)
    void should_fail_assertions(String githubOrgRepo, String githubRefName, String previousRefName,
            String expectedErrorMsg) {
        thenThrownBy(() -> Input.assertInputs(githubOrgRepo, githubRefName, previousRefName))
            .hasMessageContaining(expectedErrorMsg);
    }

}
