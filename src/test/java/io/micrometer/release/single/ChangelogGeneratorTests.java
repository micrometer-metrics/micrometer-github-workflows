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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.micrometer.release.JavaHomeFinder;
import io.micrometer.release.common.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenNoException;

class ChangelogGeneratorTests {

    // Port fixed so it matches WireMock stubs
    @RegisterExtension
    static WireMockExtension wm1 = WireMockExtension.newInstance().options(wireMockConfig().port(60006)).build();

    @Test
    void should_produce_changelog_output_for_micrometer() throws Exception {
        URL resource = ChangelogGeneratorTests.class.getResource("/generator/github-changelog-generator.jar");
        File output = Files.createTempFile("github-changelog-generator", ".md").toFile();

        ChangelogGenerator generator = testChangelogGenerator(output);

        generator.generateChangelog("v1.14.0", "micrometer-metrics/micrometer", new File(resource.toURI()));

        String content = Files.readString(
                new File(ChangelogGeneratorTests.class.getResource("/generator/micrometer.md").toURI()).toPath());
        then(output).hasContent(content);
    }

    @Test
    void should_construct_instance() {
        thenNoException().isThrownBy(() -> new ChangelogGenerator(new ProcessRunner()));
    }

    static ChangelogGenerator testChangelogGenerator(String ghApi, File changelogOutput) {
        return new ChangelogGenerator(ghApi, changelogOutput) {
            @Override
            String getJava() {
                return JavaHomeFinder.findJavaExecutablePath();
            }
        };
    }

    static ChangelogGenerator testChangelogGenerator(File changelogOutput) {
        return testChangelogGenerator("http://localhost:60006", changelogOutput);
    }

}
