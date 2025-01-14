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

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ChangelogProcessor {

    static final String OUTPUT_FILE = "changelog-output.md";

    private final File outputFile;

    ChangelogProcessor() {
        this.outputFile = new File(OUTPUT_FILE);
    }

    ChangelogProcessor(File outputFile) {
        this.outputFile = outputFile;
    }

    void processChangelog(File changelog) throws Exception {
        Set<String> testAndOptional = fetchTestAndOptionalDependencies();
        processChangelog(testAndOptional, changelog);
    }

    private Set<String> fetchTestAndOptionalDependencies() throws Exception {
        System.out.println("Fetching test and optional dependencies...");
        List<String> projectLines = projectLines();
        List<String> subprojects = projectLines
            .stream()
            .filter(line -> line.contains("Project"))
            .map(line -> line.substring(line.indexOf(":") + 1).trim())
            .toList();

        System.out.println("Subprojects: " + subprojects);

        Set<String> testOptionalDependencies = new HashSet<>();
        Set<String> implementationDependencies = new HashSet<>();

        if (!subprojects.isEmpty()) {
            List<String> gradleCommand = new ArrayList<>();
            gradleCommand.add("./gradlew");
            subprojects.forEach(subproject -> gradleCommand.add(subproject + ":dependencies"));

            try (InputStream inputStream = dependenciesInputStream(
                gradleCommand); BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("+---") || line.startsWith("\\---")) {
                        String[] parts = line.split("[: ]");
                        String dependency = parts[1] + ":" + parts[2];
                        if (line.contains("testCompile") || line.contains("testImplementation")) {
                            testOptionalDependencies.add(dependency);
                        } else {
                            implementationDependencies.add(dependency);
                        }
                    }
                }
            }

            testOptionalDependencies.removeAll(implementationDependencies);
            System.out.println("Excluded dependencies: " + testOptionalDependencies);
        }

        return testOptionalDependencies;
    }

    InputStream dependenciesInputStream(List<String> gradleCommand) throws Exception {
        Process depProcess = new ProcessBuilder(gradleCommand).start();
        return depProcess.getInputStream();
    }

    List<String> projectLines() throws Exception {
        Process process = new ProcessBuilder("./gradlew", "projects").start();
        try (BufferedReader bufferedReader = new BufferedReader(
            new InputStreamReader(process.getInputStream()))) {
            return bufferedReader.lines().toList();
        }
    }

    private void processChangelog(Set<String> excludedDependencies, File changelog) throws IOException {
        System.out.println("Processing changelog...");
        List<String> lines = Files.readAllLines(changelog.toPath());
        List<String> header = new ArrayList<>();
        List<String> dependencyLines = new ArrayList<>();
        List<String> footer = new ArrayList<>();

        boolean inDependencySection = false;

        for (String line : lines) {
            if (line.startsWith("## :hammer: Dependency Upgrades")) {
                inDependencySection = true;
                header.add(line);
                header.add("");
                break;
            }
            header.add(line);
        }

        if (inDependencySection) {
            for (String line : lines.subList(header.size(), lines.size())) {
                if (line.startsWith("## :heart: Contributors")) {
                    break;
                }
                dependencyLines.add(line);
            }
        }

        for (String line : lines) {
            if (line.startsWith("## :heart: Contributors")) {
                footer = lines.subList(lines.indexOf(line), lines.size());
                break;
            }
        }

        List<String> processedDependencies = processDependencyUpgrades(dependencyLines,
            excludedDependencies);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath())) {
            for (String line : header) {
                writer.write(line + "\n");
            }
            for (String line : processedDependencies) {
                writer.write(line + "\n");
            }
            writer.write("\n");
            for (String line : footer) {
                writer.write(line + "\n");
            }
        }
    }

    private List<String> processDependencyUpgrades(List<String> dependencyLines,
        Set<String> excludedDependencies) {
        Map<String, DependencyUpgrade> upgrades = new HashMap<>();
        Pattern pattern = Pattern.compile(
            "- Bump (.+?) from ([\\d.]+) to ([\\d.]+) (\\[#[\\d]+])\\((.+)\\)");

        for (String line : dependencyLines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String unit = matcher.group(1);
                String oldVersion = matcher.group(2);
                String newVersion = matcher.group(3);
                String prNumber = matcher.group(4);
                String url = matcher.group(5);

                if (!excludedDependencies.contains(unit)) {
                    upgrades.putIfAbsent(unit,
                        new DependencyUpgrade(unit, oldVersion, newVersion, url, prNumber));
                    DependencyUpgrade existing = upgrades.get(unit);
                    existing.updateVersions(oldVersion, newVersion);
                }
            }
        }

        return upgrades.values().stream()
            .sorted(Comparator.comparing(DependencyUpgrade::getUnit))
            .map(DependencyUpgrade::toString)
            .toList();
    }

    private static class DependencyUpgrade {

        private final String unit;
        private String lowestVersion;
        private String highestVersion;
        private final String url;
        private final String prNumber;

        public DependencyUpgrade(String unit, String lowestVersion, String highestVersion,
            String url,
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
            return String.format("- Bump %s from %s to %s [%s](%s)", unit, lowestVersion,
                highestVersion, prNumber, url);
        }
    }
}
