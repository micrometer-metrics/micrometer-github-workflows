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
package io.micrometer.release.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GradleParser {

    private static final Logger log = LoggerFactory.getLogger(GradleParser.class);

    private final List<String> excludedDependencyScopes = List.of("testCompile",
        "testImplementation", "checkstyle",
        "runtime", "nohttp", "testRuntime", "optional");

    private final ProcessRunner processRunner;

    public GradleParser(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    public Set<Dependency> fetchAllDependencies() {
        log.info("Fetching test and optional dependencies...");
        List<String> projectLines = projectLines();
        List<String> subprojects = projectLines.stream()
            .filter(line -> line.contains("Project") && line.contains(":") && line.contains("'"))
            .map(line -> line.substring(line.indexOf(":") + 1, line.lastIndexOf("'")).trim())
            .toList();

        log.info("Subprojects: {}", subprojects);

        Set<Dependency> dependencies = new HashSet<>();

        if (!subprojects.isEmpty()) {
            List<String> gradleCommand = new ArrayList<>();
            gradleCommand.add("./gradlew");
            subprojects.forEach(subproject -> gradleCommand.add(subproject + ":dependencies"));

            boolean testOrOptional = false;
            for (String line : dependenciesLines(gradleCommand)) {
                if (line.startsWith("+---") || line.startsWith("\\---")) {
                    String[] parts = line.split("[: ]");
                    String version = extractVersion(line);
                    boolean finalTestOrOptional = testOrOptional;
                    dependencies.stream()
                        .filter(dependency -> dependency.group().equalsIgnoreCase(parts[1])
                            && dependency.artifact().equalsIgnoreCase(parts[2]))
                        .findFirst()
                        .ifPresentOrElse(dependency -> {
                            log.debug("Dependency {} is already present in compile scope",
                                parts[1] + ":" + parts[2]);
                            if (dependency.toIgnore() && !finalTestOrOptional) {
                                log.debug(
                                    "Dependency {} was previously set in test or compile scope and will be in favour of one in compile scope",
                                    dependency);
                                dependencies.remove(dependency);
                                dependencies.add(new Dependency(parts[1], parts[2], version,
                                    finalTestOrOptional));
                            }
                        }, () -> dependencies.add(
                            new Dependency(parts[1], parts[2], version, finalTestOrOptional)));
                } else if (excludedDependencyScopes.stream()
                    .anyMatch(string -> line.toLowerCase().contains(string.toLowerCase()))) {
                    testOrOptional = true;
                } else if (line.isEmpty() || line.isBlank()) {
                    testOrOptional = false;
                }
            }
        }
        return dependencies;
    }

    static String extractVersion(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        if (line.contains("->")) {
            String[] parts = line.split("->");
            if (parts.length > 1) {
                return parts[1].trim().split("\\s+")[0];
            }
            return null;
        }
        String[] parts = line.split(":");
        if (parts.length < 2) {
            return null;
        }
        if (parts.length >= 3) {
            return parts[2].trim().split("\\s+")[0];
        }
        return null;
    }

    public List<String> dependenciesLines(List<String> gradleCommand) {
        return processRunner.runSilently(gradleCommand);
    }

    public List<String> projectLines() {
        return processRunner.runSilently("./gradlew", "projects");
    }

}
