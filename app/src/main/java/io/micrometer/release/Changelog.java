package io.micrometer.release;

import io.micrometer.release.ChangelogSection.Section;

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
