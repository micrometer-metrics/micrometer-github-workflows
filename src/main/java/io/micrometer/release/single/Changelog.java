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

import io.micrometer.release.single.ChangelogSection.Section;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

class Changelog {

    private final List<String> header = new ArrayList<>();

    private final Map<Section, ChangelogSection> sections = new LinkedHashMap<>();

    void addSection(ChangelogSection section) {
        sections.put(section.getSection(), section);
    }

    ChangelogSection getSection(Section section) {
        return sections.computeIfAbsent(section, ChangelogSection::new);
    }

    List<String> getHeader() {
        return new ArrayList<>(header);
    }

    Collection<ChangelogSection> getSections() {
        return sections.values().stream().sorted(Comparator.comparing(ChangelogSection::getSection)).toList();
    }

    static Changelog parse(File file) throws IOException {
        Changelog changelog = new Changelog();
        List<String> lines = Files.readAllLines(file.toPath());

        ChangelogSection currentSection = null;

        for (String line : lines) {
            if (line.startsWith("## ")) {
                currentSection = changelog.getSection(Section.fromTitle(line.substring(3).trim()));
            }
            else if (currentSection != null && !line.trim().isEmpty()) {
                currentSection.addEntry(line);
            }

        }

        return changelog;
    }

}
