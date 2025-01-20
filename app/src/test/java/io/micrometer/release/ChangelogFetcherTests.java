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

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;

import org.junit.jupiter.api.Test;

class ChangelogFetcherTests {

    @Test
    void should_fetch_changelog() {
        ProcessRunner processRunner = mock();
        File output = new File(".");
        ChangelogFetcher changelogFetcher = new ChangelogFetcher(output, processRunner);

        File changelog = changelogFetcher.fetchChangelog("v1.13.8", "micrometer-metrics/micrometer");

        then(changelog).isSameAs(output);
        verify(processRunner).run("sh", "-c",
                String.format("gh release view %s --repo %s/%s --json body --jq .body > %s", "v1.13.8",
                        "micrometer-metrics", "micrometer", changelog.getAbsolutePath()));
    }

    static ChangelogFetcher testChangelogFetcher(File output) {
        return new ChangelogFetcher(output, mock(ProcessRunner.class));
    }

}
