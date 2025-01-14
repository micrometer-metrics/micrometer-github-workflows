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

class ChangelogGeneratorDownloader {

    private static final String CHANGELOG_GENERATOR_JAR = "github-changelog-generator.jar";

    private static final String CHANGELOG_GENERATOR_VERSION = "0.0.11";

    private static final String CHANGELOG_GENERATOR_URL = "https://github.com/spring-io/github-changelog-generator/releases/download/v%s/github-changelog-generator.jar";

    private final String changelogGeneratorUrl;

    private final String changelogGeneratorJar;

    // for tests
    ChangelogGeneratorDownloader(String changelogGeneratorUrl, String changelogGeneratorJar) {
        this.changelogGeneratorUrl = changelogGeneratorUrl;
        this.changelogGeneratorJar = changelogGeneratorJar;
    }

    ChangelogGeneratorDownloader() {
        this.changelogGeneratorUrl = CHANGELOG_GENERATOR_URL;
        this.changelogGeneratorJar = CHANGELOG_GENERATOR_JAR;
    }

    File downloadChangelogGenerator() throws Exception {
        if (!Files.exists(Paths.get(changelogGeneratorJar))) {
            download();
        }
        else {
            System.out.println("GitHub Changelog Generator already downloaded.");
        }
        return new File(changelogGeneratorJar);
    }

    void download() throws Exception {
        System.out.println("Downloading GitHub Changelog Generator...");
        String changelogGeneratorVersion = System.getenv("CHANGELOG_GENERATOR_VERSION");
        changelogGeneratorVersion = changelogGeneratorVersion == null ? CHANGELOG_GENERATOR_VERSION
                : changelogGeneratorVersion;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(changelogGeneratorUrl.formatted(changelogGeneratorVersion)))
            .GET()
            .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<Path> response = client.send(request,
                HttpResponse.BodyHandlers.ofFile(Paths.get(changelogGeneratorJar)));

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download GitHub Changelog Generator");
        }
    }

}
