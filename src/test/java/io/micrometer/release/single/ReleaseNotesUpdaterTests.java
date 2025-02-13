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

import static org.mockito.Mockito.mock;

import io.micrometer.release.common.ProcessRunner;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReleaseNotesUpdaterTests {

    @Test
    void should_update_release_notes() {
        ProcessRunner processRunner = mock();
        ReleaseNotesUpdater releaseNotesUpdater = new ReleaseNotesUpdater(processRunner);
        File changelog = new File(".");

        releaseNotesUpdater.updateReleaseNotes("v1.0.0", changelog);

        Mockito.verify(processRunner)
            .run("gh", "release", "edit", "v1.0.0", "--notes-file", changelog.getAbsolutePath());
    }

}
