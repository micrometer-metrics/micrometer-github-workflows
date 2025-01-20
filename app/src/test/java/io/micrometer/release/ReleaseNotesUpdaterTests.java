package io.micrometer.release;

import static org.mockito.Mockito.mock;

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
