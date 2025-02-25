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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * Our release slot on the release train is the second Monday of the month for patch
 * releases. It's a little more complicated with milestone releases - they are on the same
 * day when they happen, but after M3 is RC1 and after that is of course the GA version.
 * After the GA version there's a month with only patch releases.
 */
class ReleaseDateCalculatorTests {

    private static final Logger log = LoggerFactory.getLogger(ReleaseDateCalculatorTests.class);

    @ParameterizedTest(name = "{index} For current date <{0}> expected next release date should be <{1}>")
    @CsvSource(textBlock = """
            2025-01-01, 2025-02-10
            2025-01-14, 2025-02-10
            2025-09-01, 2025-10-13
            """)
    // 2025-01-01 is Tuesday
    // 2025-09-01 is Monday
    void should_calculate_next_milestone_date(LocalDate now, LocalDate expectedDeadline) {
        log.info("Current date [{}] is [{}]", now, now.getDayOfWeek());
        log.info("Expected date [{}] is [{}]", expectedDeadline, expectedDeadline.getDayOfWeek());
        LocalDate milestoneDate = ReleaseDateCalculator.calculateDueDate(now);

        then(milestoneDate).isEqualTo(expectedDeadline);
    }

}
