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

import io.micrometer.release.ChangelogSection.Section;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChangelogProcessor {

    private static final Logger log = LoggerFactory.getLogger(ChangelogProcessor.class);

    static final String OUTPUT_FILE = "changelog-output.md";

    private final List<String> excludedDependencyScopes = List.of("testCompile", "testImplementation", "checkstyle",
            "runtime", "nohttp", "testRuntime", "optional");

    private final File outputFile;

    private final ProcessRunner processRunner;

    ChangelogProcessor(ProcessRunner processRunner) {
        this.processRunner = processRunner;
        this.outputFile = new File(OUTPUT_FILE);
    }

    ChangelogProcessor(ProcessRunner processRunner, File changelogOutput) {
        this.processRunner = processRunner;
        this.outputFile = changelogOutput;
    }

    File processChangelog(File changelog, File oldChangelog) throws Exception {
        Set<String> testAndOptional = fetchTestAndOptionalDependencies();

        Changelog currentChangelog = Changelog.parse(changelog);
        Changelog oldChangelogContent = oldChangelog != null ? Changelog.parse(oldChangelog) : new Changelog();

        // Merge changelogs
        for (ChangelogSection oldSection : oldChangelogContent.getSections()) {
            ChangelogSection currentSection = currentChangelog.getSection(oldSection.getSection());
            currentSection.merge(oldSection);
        }

        // Process dependencies section specially
        ChangelogSection depsSection = currentChangelog.getSection(Section.UPGRADES);
        Collection<String> processedDeps = processDependencyUpgrades(depsSection.getEntries(), testAndOptional);
        depsSection.clear();
        processedDeps.forEach(depsSection::addEntry);

        // Write the result
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath())) {
            // Write header
            for (String line : currentChangelog.getHeader()) {
                writer.write(line + "\n");
            }

            // Write sections
            for (ChangelogSection section : currentChangelog.getSections()) {
                writer.write("## " + section.getTitle() + "\n\n");

                List<String> sortedEntries = new ArrayList<>(section.getEntries());
                Collections.sort(sortedEntries);

                for (String entry : sortedEntries) {
                    writer.write(entry + "\n");
                }
                writer.write("\n");
            }
        }
        return outputFile;
    }

    private Set<String> fetchTestAndOptionalDependencies() {
        log.info("Fetching test and optional dependencies...");
        List<String> projectLines = projectLines();
        List<String> subprojects = projectLines.stream()
            .filter(line -> line.contains("Project"))
            .map(line -> line.substring(line.indexOf(":") + 1, line.lastIndexOf("'")).trim())
            .toList();

        log.info("Subprojects: {}", subprojects);

        Set<String> testOptionalDependencies = new HashSet<>();
        Set<String> implementationDependencies = new HashSet<>();

        if (!subprojects.isEmpty()) {
            List<String> gradleCommand = new ArrayList<>();
            gradleCommand.add("./gradlew");
            subprojects.forEach(subproject -> gradleCommand.add(subproject + ":dependencies"));

            boolean testOrOptional = false;
            for (String line : dependenciesLines(gradleCommand)) {
                if (line.startsWith("+---") || line.startsWith("\\---")) {
                    String[] parts = line.split("[: ]");
                    String dependency = parts[1] + ":" + parts[2];
                    if (testOrOptional) {
                        testOptionalDependencies.add(dependency);
                    }
                    else {
                        implementationDependencies.add(dependency);
                    }
                }
                else if (excludedDependencyScopes.stream()
                    .anyMatch(string -> line.toLowerCase().contains(string.toLowerCase()))) {
                    testOrOptional = true;
                }
                else if (line.isEmpty() || line.isBlank()) {
                    testOrOptional = false;
                }
            }

            testOptionalDependencies.removeAll(implementationDependencies);
            log.info("Excluded dependencies: {}", testOptionalDependencies);
        }

        return testOptionalDependencies;
    }

    List<String> dependenciesLines(List<String> gradleCommand) {
        return processRunner.run(gradleCommand);
    }

    List<String> projectLines() {
        return processRunner.run("./gradlew", "projects");
    }

    private Collection<String> processDependencyUpgrades(Iterable<String> dependencyLines,
            Set<String> excludedDependencies) {
        Map<String, DependencyUpgrade> upgrades = new HashMap<>();
        Pattern pattern = Pattern.compile("- Bump (.+?) from ([\\d.]+) to ([\\d.]+) \\[(#[\\d]+)]\\((.+)\\)");

        for (String line : dependencyLines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String unit = matcher.group(1);
                String oldVersion = matcher.group(2);
                String newVersion = matcher.group(3);
                String prNumber = matcher.group(4);
                String url = matcher.group(5);

                if (!excludedDependencies.contains(unit)) {
                    upgrades.putIfAbsent(unit, new DependencyUpgrade(unit, oldVersion, newVersion, url, prNumber));
                    DependencyUpgrade existing = upgrades.get(unit);
                    existing.updateVersions(oldVersion, newVersion);
                }
            }
        }

        return upgrades.values()
            .stream()
            .sorted(Comparator.comparing(DependencyUpgrade::getUnit))
            .map(DependencyUpgrade::toString)
            .distinct()
            .toList();
    }

    private static class DependencyUpgrade {

        private final String unit;

        private String lowestVersion;

        private String highestVersion;

        private final String url;

        private final String prNumber;

        public DependencyUpgrade(String unit, String lowestVersion, String highestVersion, String url,
                String prNumber) {
            this.unit = unit;
            this.lowestVersion = lowestVersion;
            this.highestVersion = highestVersion;
            this.url = url;
            this.prNumber = prNumber;
        }

        public void updateVersions(String oldVersion, String newVersion) {
            if (oldVersion.compareTo(lowestVersion) < 0) {
                lowestVersion = oldVersion;
            }
            if (newVersion.compareTo(highestVersion) > 0) {
                highestVersion = newVersion;
            }
        }

        public String getUnit() {
            return unit;
        }

        @Override
        public String toString() {
            return String.format("- Bump %s from %s to %s [%s](%s)", unit, lowestVersion, highestVersion, prNumber,
                    url);
        }

    }

}
