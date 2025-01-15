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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChangelogGenerator {

    private static final Logger log = LoggerFactory.getLogger(ChangelogGenerator.class);

    static final String INPUT_FILE = "changelog.md";
    static final String GITHUB_API_URL = "https://api.github.com";

    private final String githubApi;

    private final String githubRefName;

    private final String githubRepository;

    private final String githubToken;

    private final String outputFile;

    public ChangelogGenerator() {
        this.githubRefName = System.getenv("GITHUB_REF_NAME");
        this.githubRepository = System.getenv("GITHUB_REPOSITORY");
        this.githubApi = GITHUB_API_URL;
        this.githubToken = System.getenv("GITHUB_TOKEN");
        this.outputFile = INPUT_FILE;
    }

    // for tests
    ChangelogGenerator(String githubApi, String githubRefName, String githubRepository, String outputFile) {
        this.githubApi = githubApi;
        this.githubRefName = githubRefName;
        this.githubRepository = githubRepository;
        this.outputFile = outputFile;
        this.githubToken = "";
    }

    File generateChangelog(File jarPath) throws IOException, InterruptedException {
        log.info("Generating changelog...");
        ProcessBuilder processBuilder = new ProcessBuilder(getJava(), "-jar", jarPath.getAbsolutePath(),
                githubRefName.replace("v", ""), outputFile, "--changelog.repository=" + githubRepository,
                "--github.api-url=" + githubApi, "--github.token=" + githubToken);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(line);
            }
        }

        if (process.waitFor() != 0) {
            throw new RuntimeException("Failed to generate changelog");
        }
        return new File(outputFile);
    }

    String getJava() {
        return "java";
    }

}
