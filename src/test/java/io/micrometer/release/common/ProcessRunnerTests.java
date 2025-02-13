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
package io.micrometer.release.common;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ProcessRunnerTests {

    private static final String REPO = "repo";

    Process process = mock();

    ByteArrayInputStream stream = new ByteArrayInputStream("Hello".getBytes());

    ByteArrayInputStream errorStream = new ByteArrayInputStream(new byte[0]);

    @BeforeEach
    void setUp() {
        given(process.getInputStream()).willReturn(stream);
        given(process.getErrorStream()).willReturn(errorStream);
    }

    @AfterEach
    void tearDown() throws IOException {
        stream.close();
    }

    @Test
    void should_run_process() throws IOException {
        File tempFile = Files.createTempFile("micrometer-release", ".txt").toFile();
        tempFile.delete();
        then(tempFile).doesNotExist();

        new ProcessRunner().run("touch", tempFile.getAbsolutePath());

        then(tempFile).exists();
    }

    @Test
    void should_return_process_output() throws InterruptedException {
        given(process.waitFor()).willReturn(0);

        then(stubReturingProcessRunner().run("whatever")).containsExactly("Hello");
    }

    @Test
    void should_run_git_for_gradle_command() throws InterruptedException {
        given(process.waitFor()).willReturn(0);
        StubReturningGradleProcessRunner runner = new StubReturningGradleProcessRunner(process);

        runner.run("./gradlew", "projects");

        then(runner.gitConfigRan).isTrue();
    }

    @Test
    void should_append_repo_for_gh_command_that_is_not_api() throws InterruptedException {
        given(process.waitFor()).willReturn(0);

        then(stubReturingCommandAssertingProcessRunner(
                strings -> then(strings).containsExactly("gh", "foo", "bar", "--repo", REPO))
            .run("gh", "foo", "bar")).isEqualTo(List.of("Hello"));
    }

    @Test
    void should_not_append_repo_for_gh_command_that_is_api() throws InterruptedException {
        given(process.waitFor()).willReturn(0);

        then(stubReturingCommandAssertingProcessRunner(
                strings -> then(strings).containsExactly("gh", "api", "foo", "bar"))
            .run("gh", "api", "foo", "bar")).isEqualTo(List.of("Hello"));
    }

    @Test
    void should_throw_an_exception_when_process_failed_to_start() {
        thenThrownBy(() -> exceptionThrowingProcessRunner().run("whatever")).hasRootCauseMessage("BOOM!")
            .hasMessageContaining("A failure around the process execution happened");
    }

    @Test
    void should_throw_an_exception_when_process_failed_to_return() throws InterruptedException {
        given(process.waitFor()).willReturn(-1);

        thenThrownBy(() -> stubReturingProcessRunner().run("whatever"))
            .hasMessageContaining("Failed to run the command [whatever]");
    }

    private ProcessRunner exceptionThrowingProcessRunner() {
        return new ProcessRunner(REPO) {
            @Override
            Process startProcess(String[] processedCommand) throws IOException {
                throw new IOException("BOOM!");
            }
        };
    }

    private ProcessRunner stubReturingProcessRunner() {
        return new ProcessRunner(REPO) {
            @Override
            Process startProcess(String[] processedCommand) {
                return process;
            }
        };
    }

    private ProcessRunner stubReturingCommandAssertingProcessRunner(Consumer<String[]> assertion) {
        return new ProcessRunner(REPO) {
            @Override
            Process startProcess(String[] processedCommand) {
                assertion.accept(processedCommand);
                return process;
            }
        };
    }

    static class StubReturningGradleProcessRunner extends ProcessRunner {

        private final Process process;

        private boolean gitConfigRan;

        StubReturningGradleProcessRunner(Process process) {
            super(REPO);
            this.process = process;
        }

        @Override
        Process doStartProcess(ProcessBuilder processBuilder) {
            return process;
        }

        @Override
        void runGitConfig() {
            gitConfigRan = true;
        }

    }

}
