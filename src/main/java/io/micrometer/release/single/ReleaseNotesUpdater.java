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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

class ReleaseNotesUpdater {

    private static final Logger log = LoggerFactory.getLogger(ReleaseNotesUpdater.class);

    private final ProcessRunner processRunner;

    ReleaseNotesUpdater(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    void updateReleaseNotes(String githubRef, File changelog) {
        log.info("Updating release notes...");
        processRunner.run("gh", "release", "edit", githubRef, "--notes-file", changelog.getAbsolutePath());
    }

}
