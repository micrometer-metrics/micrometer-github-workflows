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
package io.micrometer.release.single;

import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.single.ChangelogSection.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class ChangelogProcessor {

    private static final Logger log = LoggerFactory.getLogger(ChangelogProcessor.class);

    static final String OUTPUT_FILE = "changelog-output.md";

    private final File outputFile;

    private final GradleParser gradleParser;

    ChangelogProcessor(ProcessRunner processRunner) {
        this.outputFile = new File(OUTPUT_FILE);
        this.gradleParser = new GradleParser(processRunner);
    }

    ChangelogProcessor(File changelogOutput, GradleParser gradleParser) {
        this.outputFile = changelogOutput;
        this.gradleParser = gradleParser;
    }

    File processChangelog(File changelog, File oldChangelog) throws Exception {
        log.info("Starting to process changelog...");
        Set<Dependency> dependencies = fetchAllDependencies();
        Set<Dependency> testOrOptional = dependencies.stream().filter(Dependency::toIgnore).collect(Collectors.toSet());

        Changelog currentChangelog = Changelog.parse(changelog);
        Changelog oldChangelogContent = oldChangelog != null ? Changelog.parse(oldChangelog) : new Changelog();

        // Merge changelogs
        for (ChangelogSection oldSection : oldChangelogContent.getSections()) {
            ChangelogSection currentSection = currentChangelog.getSection(oldSection.getSection());
            currentSection.merge(oldSection);
        }

        // Process dependencies section specially
        ChangelogSection depsSection = currentChangelog.getSection(Section.UPGRADES);
        Collection<String> processedDeps = processDependencyUpgrades(depsSection.getEntries(),
                testOrOptional.stream()
                    .map(dependency -> dependency.group() + ":" + dependency.artifact())
                    .collect(Collectors.toSet()));
        depsSection.clear();
        processedDeps.forEach(depsSection::addEntry);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath())) {

            for (String line : currentChangelog.getHeader()) {
                writer.write(line + "\n");
            }

            for (ChangelogSection section : currentChangelog.getSections()) {
                List<String> sortedEntries = new ArrayList<>(section.getEntries());
                if (sortedEntries.isEmpty()) {
                    continue;
                }
                Collections.sort(sortedEntries);
                writer.write("## " + section.getTitle() + "\n\n");
                for (String entry : sortedEntries) {
                    writer.write(entry + "\n");
                }
                writer.write("\n");
            }
        }
        log.info("Changelog processed");
        return outputFile;
    }

    private Set<Dependency> fetchAllDependencies() {
        return gradleParser.fetchAllDependencies();
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
