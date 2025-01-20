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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChangelogGeneratorDownloader {

    private static final Logger log = LoggerFactory.getLogger(ChangelogGeneratorDownloader.class);

    private static final String CHANGELOG_GENERATOR_JAR = "github-changelog-generator.jar";

    private static final String CHANGELOG_GENERATOR_VERSION = "0.0.11";

    static final String CHANGELOG_GENERATOR_URL = "https://github.com/spring-io/github-changelog-generator/releases/download/v%s/github-changelog-generator.jar";

    private final String changelogGeneratorUrl;

    private final File changelogGeneratorJar;

    // for tests
    ChangelogGeneratorDownloader(String changelogGeneratorUrl, File changelogGeneratorJar) {
        this.changelogGeneratorUrl = changelogGeneratorUrl;
        this.changelogGeneratorJar = changelogGeneratorJar;
    }

    ChangelogGeneratorDownloader() {
        this.changelogGeneratorUrl = CHANGELOG_GENERATOR_URL;
        this.changelogGeneratorJar = new File(CHANGELOG_GENERATOR_JAR);
    }

    File downloadChangelogGenerator() throws Exception {
        if (!Files.exists(changelogGeneratorJar.toPath())) {
            download();
        }
        else {
            log.info("GitHub Changelog Generator already downloaded.");
        }
        return changelogGeneratorJar;
    }

    void download() throws Exception {
        log.info("Downloading GitHub Changelog Generator to [{}]...", changelogGeneratorJar.getAbsolutePath());
        String changelogGeneratorVersion = System.getenv("CHANGELOG_GENERATOR_VERSION");
        changelogGeneratorVersion = changelogGeneratorVersion == null ? CHANGELOG_GENERATOR_VERSION
                : changelogGeneratorVersion;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(changelogGeneratorUrl.formatted(changelogGeneratorVersion)))
            .GET()
            .build();
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL) // This will follow redirects
            .build();
        HttpResponse<Path> response = client.send(request,
                HttpResponse.BodyHandlers.ofFile(changelogGeneratorJar.toPath()));

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Failed to download GitHub Changelog Generator");
        }
    }

}
