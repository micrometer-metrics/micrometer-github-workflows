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

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChangelogGenerator {

    private static final Logger log = LoggerFactory.getLogger(ChangelogGenerator.class);

    static final String INPUT_FILE = "changelog.md";
    static final String GITHUB_API_URL = "https://api.github.com";

    private final String githubApi;

    private final String githubToken;

    private final File outputFile;

    private final ProcessRunner processRunner;

    public ChangelogGenerator() {
        this.githubApi = GITHUB_API_URL;
        this.githubToken = System.getenv("GITHUB_TOKEN");
        this.outputFile = new File(INPUT_FILE);
        this.processRunner = new ProcessRunner();
    }

    // for tests
    ChangelogGenerator(String githubApi, File outputFile) {
        this.githubApi = githubApi;
        this.outputFile = outputFile;
        this.githubToken = "";
        this.processRunner = new ProcessRunner();
    }

    File generateChangelog(String githubRefName, String githubOrgRepo, File jarPath) {
        log.info("Generating changelog...");
        processRunner.run(getJava(), "-jar", jarPath.getAbsolutePath(), githubRefName.replace("v", ""),
                outputFile.getAbsolutePath(), "--changelog.repository=" + githubOrgRepo,
                "--github.api-url=" + githubApi, "--github.token=" + githubToken);
        return outputFile;
    }

    String getJava() {
        return "java";
    }

}
