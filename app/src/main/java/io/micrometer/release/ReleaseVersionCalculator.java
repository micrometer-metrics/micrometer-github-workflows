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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ReleaseVersionCalculator {

    private static final Logger log = LoggerFactory.getLogger(ReleaseVersionCalculator.class);

    static String calculateNextVersion(String title) {
        String[] parts = title.split("\\.");
        // GA version
        String nextVersion;
        if (!title.contains("-")) {
            nextVersion = parts[0] + "." + parts[1] + "." + (Integer.parseInt(parts[2]) + 1);
            log.info("Version is GA will create a new milestone {}", nextVersion);
        }
        else {
            // 1.0.0-M1, 1.0.0-RC1
            String[] split = title.split("-");
            if (split.length != 2) {
                throw new IllegalStateException("Milestone title [" + title + "] contains invalid format");
            }
            // M1, RC1
            String suffix = split[1];
            if (suffix.contains("M")) {
                int number = Integer.parseInt(suffix.substring(1));
                if (number < 3) {
                    // 1.0.0-M1 -> 1.0.0-M2
                    nextVersion = split[0] + "-" + "M" + (number + 1);
                    log.info("Version is Milestone but there will be another milestone {}", nextVersion);
                }
                else if (number == 3) {
                    // 1.0.0-M3 -> 1.0.0-RC1
                    nextVersion = split[0] + "-RC1";
                    log.info("Version is M3 next, we will have {}", nextVersion);
                }
                else {
                    throw new IllegalStateException(
                            "Milestone title [" + title + "] contains invalid format (we accept M[1-3] or RC1)");
                }
            }
            else if (suffix.contains("RC1")) {
                // 1.0.0-RC1 -> 1.0.0
                nextVersion = split[0];
                log.info("Version is RC1, next we will have GA {}", nextVersion);
            }
            else {
                throw new IllegalStateException(
                        "Milestone title [" + title + "] contains invalid format (we accept M[1-3] or RC1)");
            }
        }
        return nextVersion;
    }

}
