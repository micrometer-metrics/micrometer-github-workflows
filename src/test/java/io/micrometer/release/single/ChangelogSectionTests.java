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

import static org.assertj.core.api.BDDAssertions.then;

import io.micrometer.release.single.ChangelogSection.Section;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChangelogSectionTests {

    @Test
    void should_add_entries() {
        ChangelogSection section = new ChangelogSection(Section.UPGRADES);

        section.addEntry("foo");

        then(section.getEntries()).containsExactly("foo");
    }

    @Test
    void should_get_title() {
        ChangelogSection section = new ChangelogSection(Section.UPGRADES);

        then(section.getTitle()).isEqualTo(":hammer: Dependency Upgrades");
    }

    @Test
    void should_clear_entries() {
        ChangelogSection section = new ChangelogSection(Section.UPGRADES);
        section.addEntry("foo");
        then(section.getEntries()).hasSize(1);

        section.clear();

        then(section.getEntries()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Section.class)
    void should_merge_entries_with_no_changes(Section section) {
        if (section == Section.CONTRIBUTORS) {
            Assumptions.abort("Upgrades have more sophisticated logic");
        }
        ChangelogSection oneSection = new ChangelogSection(section);
        oneSection.addEntry("foo");
        ChangelogSection anotherSection = new ChangelogSection(section);
        anotherSection.addEntry("bar");

        oneSection.merge(anotherSection);

        then(oneSection.getEntries()).containsOnly("bar", "foo");
    }

    @Test
    void should_not_merge_sections_of_different_types() {
        ChangelogSection oneSection = new ChangelogSection(Section.UPGRADES);
        oneSection.addEntry("foo");
        ChangelogSection anotherSection = new ChangelogSection(Section.BUGS);
        anotherSection.addEntry("bar");

        oneSection.merge(anotherSection);

        then(oneSection.getEntries()).containsExactly("foo");
    }

}
