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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.io.IOException;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;

class ChangelogGeneratorDownloaderTests {

    @RegisterExtension
    static WireMockExtension wm1 = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    File outputJar = new File("build", "output.jar");

    @BeforeEach
    void setup() {
        if (outputJar.exists()) {
            outputJar.delete();
        }
        then(outputJar).doesNotExist();
        wm1.stubFor(WireMock.any(WireMock.anyUrl()).willReturn(WireMock.aResponse().withBody("text")));
    }

    @Test
    void should_download_changelog_generator_when_jar_not_present() throws Exception {
        ChangelogGeneratorDownloader downloader = new ChangelogGeneratorDownloader(wm1.baseUrl(), outputJar);

        downloader.downloadChangelogGenerator();

        then(outputJar).exists();
    }

    @Test
    void should_not_download_changelog_generator_when_jar_present() throws Exception {
        outputJar.createNewFile();
        ChangelogGeneratorDownloader downloader = new ChangelogGeneratorDownloader(wm1.baseUrl(), outputJar) {
            @Override
            void download() throws IOException, InterruptedException {
                throw new AssertionError("Should not be called");
            }
        };

        downloader.downloadChangelogGenerator();

        then(outputJar).exists();
    }

}
