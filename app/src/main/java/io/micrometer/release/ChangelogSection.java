package io.micrometer.release;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChangelogSection {

    private static final String CONTRIBUTORS_TEXT = "Thank you to all the contributors who worked on this release:";

    private static final Pattern GITHUB_HANDLE = Pattern.compile("(@[a-zA-Z0-9](?:-?[a-zA-Z0-9])*[a-zA-Z0-9])");

    private static final Logger log = LoggerFactory.getLogger(ChangelogSection.class);

    enum Section {

        FEATURES(":star: New Features"), BUGS(":lady_beetle: Bug Fixes"),
        DOCUMENTATION(":notebook_with_decorative_cover: Documentation"), UPGRADES(":hammer: Dependency Upgrades"),
        CONTRIBUTORS(":heart: Contributors");

        private final String title;

        Section(String title) {
            this.title = title;
        }

        static Section fromTitle(String title) {
            return Arrays.stream(Section.values())
                .filter(section -> section.title.equalsIgnoreCase(title))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Title is not supported [" + title + "]"));
        }

    }

    private final Section section;

    private final Set<String> entries = new TreeSet<>();

    ChangelogSection(Section section) {
        this.section = section;
    }

    void addEntry(String entry) {
        entries.add(entry);
    }

    Section getSection() {
        return section;
    }

    String getTitle() {
        return section.title;
    }

    Set<String> getEntries() {
        return new HashSet<>(entries);
    }

    void clear() {
        entries.clear();
    }

    void merge(ChangelogSection other) {
        Section otherSection = other.getSection();
        if (otherSection != section) {
            log.warn("Can't merge section {} with {}", otherSection, section);
            return;
        }
        switch (otherSection) {
            case FEATURES, BUGS, DOCUMENTATION, UPGRADES:
                entries.addAll(other.getEntries());
                break;
            case CONTRIBUTORS:
                Set<String> handles = new TreeSet<>();
                addAuthors(handles, entries);
                addAuthors(handles, other.getEntries());
                entries.clear();
                entries.add(joinHandles(new ArrayList<>(handles)));
        }
    }

    private String joinHandles(List<String> handles) {
        if (handles == null || handles.isEmpty()) {
            return "";
        }
        if (handles.size() == 1) {
            return handles.get(0);
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < handles.size() - 1; i++) {
            result.append(handles.get(i)).append(", ");
        }
        result.append("and ").append(handles.get(handles.size() - 1));

        return result.toString();
    }

    private void addAuthors(Set<String> handles, Set<String> lines) {
        for (String entry : lines) {
            if (entry.equalsIgnoreCase(CONTRIBUTORS_TEXT)) {
                continue;
            }
            Matcher matcher = GITHUB_HANDLE.matcher(entry);
            while (matcher.find()) {
                handles.add(matcher.group(0));
            }
        }
    }

}
