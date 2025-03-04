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
package io.micrometer.release.train;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

class MavenCentralSyncCheckerTests {

    @RegisterExtension
    static WireMockExtension wm1 = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    MavenCentralSyncChecker mavenCentralSyncChecker = new MavenCentralSyncChecker(wm1.url("/maven2/io/micrometer/"), 1,
            1);

    @Test
    void should_check_when_artifact_present() {
        wm1.stubFor(head(urlEqualTo("/maven2/io/micrometer/micrometer-bom/1.13.3/"))
            .willReturn(aResponse().withStatus(200)));
        wm1.stubFor(head(urlEqualTo("/maven2/io/micrometer/micrometer-bom/1.14.9/"))
            .willReturn(aResponse().withStatus(200)));

        mavenCentralSyncChecker.checkIfArtifactsAreInCentral(List.of("1.13.3", "1.14.9"),
                TestProjectSetup.forMicrometer("1.13.3", "1.14.9"));

        wm1.verify(WireMock.headRequestedFor(WireMock.urlEqualTo("/maven2/io/micrometer/micrometer-bom/1.13.3/")));
        wm1.verify(WireMock.headRequestedFor(WireMock.urlEqualTo("/maven2/io/micrometer/micrometer-bom/1.14.9/")));
    }

    @Test
    void should_fail_when_artifact_missing_within_timeout() {
        BDDAssertions
            .thenThrownBy(() -> mavenCentralSyncChecker.checkIfArtifactsAreInCentral(List.of("1.13.3", "1.14.9"),
                    TestProjectSetup.forMicrometer("1.13.3", "1.14.9")))
            .hasMessageContaining("not found in Maven Central")
            .hasRootCauseInstanceOf(IllegalStateException.class);
    }

}
