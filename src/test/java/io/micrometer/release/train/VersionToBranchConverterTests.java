/**
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.micrometer.release.train;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.BDDAssertions.then;

class VersionToBranchConverterTests {

    @RegisterExtension
    static WireMockExtension wm1 = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Test
    void should_convert_version_to_branch() {
        VersionToBranchConverter converter = new VersionToBranchConverter("foo",
                wm1.url("/repos/micrometer-metrics/micrometer/branches/"), HttpClient.newBuilder().build());

        Map<String, String> versionToBranch = converter.convert(List.of("1.0.1", "1.1.2-M2"));

        then(versionToBranch).hasSize(2).containsAllEntriesOf(Map.of("1.0.1", "1.0.x", "1.1.2-M2", "main"));
    }

}
