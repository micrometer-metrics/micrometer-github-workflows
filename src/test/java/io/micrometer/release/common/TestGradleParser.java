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
package io.micrometer.release.common;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

public class TestGradleParser extends GradleParser {

    static List<String> expectedGradleCommand = List.of("./gradlew", "concurrency-tests:dependencies",
            "docs:dependencies", "micrometer-benchmarks-core:dependencies", "micrometer-bom:dependencies",
            "micrometer-commons:dependencies", "micrometer-core:dependencies", "micrometer-jakarta9:dependencies",
            "micrometer-java11:dependencies", "micrometer-jetty11:dependencies", "micrometer-jetty12:dependencies",
            "micrometer-observation:dependencies", "micrometer-observation-test:dependencies",
            "micrometer-osgi-test:dependencies", "micrometer-registry-appoptics:dependencies",
            "micrometer-registry-atlas:dependencies", "micrometer-registry-azure-monitor:dependencies",
            "micrometer-registry-cloudwatch2:dependencies", "micrometer-registry-datadog:dependencies",
            "micrometer-registry-dynatrace:dependencies", "micrometer-registry-elastic:dependencies",
            "micrometer-registry-ganglia:dependencies", "micrometer-registry-graphite:dependencies",
            "micrometer-registry-health:dependencies", "micrometer-registry-humio:dependencies",
            "micrometer-registry-influx:dependencies", "micrometer-registry-jmx:dependencies",
            "micrometer-registry-kairos:dependencies", "micrometer-registry-new-relic:dependencies",
            "micrometer-registry-opentsdb:dependencies", "micrometer-registry-otlp:dependencies",
            "micrometer-registry-prometheus:dependencies", "micrometer-registry-prometheus-simpleclient:dependencies",
            "micrometer-registry-signalfx:dependencies", "micrometer-registry-stackdriver:dependencies",
            "micrometer-registry-statsd:dependencies", "micrometer-registry-wavefront:dependencies",
            "micrometer-samples-boot2:dependencies", "micrometer-samples-boot2-reactive:dependencies",
            "micrometer-samples-core:dependencies", "micrometer-samples-hazelcast:dependencies",
            "micrometer-samples-hazelcast3:dependencies", "micrometer-samples-javalin:dependencies",
            "micrometer-samples-jersey3:dependencies", "micrometer-samples-jetty12:dependencies",
            "micrometer-samples-jooq:dependencies", "micrometer-samples-kotlin:dependencies",
            "micrometer-samples-spring-integration:dependencies", "micrometer-test:dependencies",
            "micrometer-test-aspectj-ctw:dependencies", "micrometer-test-aspectj-ltw:dependencies");

    public TestGradleParser() {
        super(new ProcessRunner());
    }

    @Override
    public List<String> projectLines() {
        return projectLinesFromFile();
    }

    static List<String> projectLinesFromFile() {
        return textFromFile("/gradle/projects_output.txt");
    }

    private static List<String> textFromFile(String name) {
        URL resource = TestGradleParser.class.getResource(name);
        try {
            return Files.readAllLines(new File(resource.toURI()).toPath());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> dependenciesLines(List<String> gradleCommand) {
        then(gradleCommand).isEqualTo(expectedGradleCommand);
        return dependenciesFromFile();
    }

    static List<String> dependenciesFromFile() {
        return textFromFile("/gradle/dependencies_output.txt");
    }

}
