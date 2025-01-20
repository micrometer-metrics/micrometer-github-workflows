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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;

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

    static ChangelogGenerator testChangelogGenerator(File changelogOutput) {
        return new ChangelogGenerator("http://localhost:60006", changelogOutput) {
            @Override
            String getJava() {
                return findJavaInstallation();
            }
        };
    }

    private static String findJavaInstallation() {
        if (isJavaAvailable("java")) {
            return "java";
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("which", "java");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Fallback to checking SDKMAN installation
        try {
            String userHome = System.getProperty("user.home");
            Path sdkmanJavaPath = Paths.get(userHome, ".sdkman", "candidates", "java", "current", "bin", "java");
            if (Files.exists(sdkmanJavaPath)) {
                return sdkmanJavaPath.toString();
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        throw new UnsupportedOperationException("Java not found!!");
    }

    private static boolean isJavaAvailable(String javaCommand) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(javaCommand, "-version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        }
        catch (Exception e) {
            return false;
        }
    }

}
