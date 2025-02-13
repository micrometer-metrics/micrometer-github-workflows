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

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

    public static final String JAVA_PATH_FOR_ECLIPSE_DOCKER_IMAGE = "/opt/java/openjdk";

    private final String repo;

    private final Map<String, String> envVars = new HashMap<>();

    public ProcessRunner() {
        this.repo = null;
    }

    public ProcessRunner(String repo) {
        this.repo = repo;
    }

    public ProcessRunner withEnvVars(Map<String, String> envVars) {
        this.envVars.putAll(envVars);
        return this;
    }

    public List<String> run(List<String> command) {
        return run(command.toArray(new String[0]));
    }

    public List<String> run(String... command) {
        List<String> lines = new ArrayList<>();
        String[] processedCommand = processCommand(command);
        try {
            Process process = startProcess(processedCommand);

            log.info("Printing out process logs:\n\n");

            List<String> errorLines = new ArrayList<>();

            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info(line);
                        lines.add(line);
                    }
                }
                catch (IOException e) {
                    log.error("Error reading process output", e);
                }
            });

            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.error(line);
                        errorLines.add(line);
                    }
                }
                catch (IOException e) {
                    log.error("Error reading process error stream", e);
                }
            });

            outputThread.start();
            errorThread.start();

            // Wait for both streams to be fully read
            outputThread.join();
            errorThread.join();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorMessage = String.format("Failed to run the command %s. Exit code: %d.%nError output:%n%s",
                        Arrays.toString(processedCommand), exitCode, String.join("\n", errorLines));
                throw new RuntimeException(errorMessage);
            }
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException("A failure around the process execution happened", e);
        }
        return lines;
    }

    Process startProcess(String... processedCommand) throws IOException, InterruptedException {
        if (isGradleCommand(processedCommand)) {
            runGitConfig();
        }
        ProcessBuilder processBuilder = new ProcessBuilder(processedCommand).redirectErrorStream(false);
        return doStartProcess(processBuilder);
    }

    Process doStartProcess(ProcessBuilder processBuilder) throws IOException {
        // TODO: Need to figure out a better way
        if (new File(JAVA_PATH_FOR_ECLIPSE_DOCKER_IMAGE).exists()) {
            processBuilder.environment().put("JAVA_HOME", JAVA_PATH_FOR_ECLIPSE_DOCKER_IMAGE);
        }
        processBuilder.environment().putAll(this.envVars);
        return processBuilder.start();
    }

    void runGitConfig() throws InterruptedException, IOException {
        doStartProcess(new ProcessBuilder("git", "config", "--global", "--add", "safe.directory", "/github/workspace"))
            .waitFor();
    }

    private boolean isGradleCommand(String[] command) {
        return command != null && command.length > 0
                && (command[0].endsWith("gradlew") || command[0].endsWith("gradle"));
    }

    private String[] processCommand(String[] command) {
        String[] processedCommand = command;
        if (repo != null && command.length > 2 && command[0].equalsIgnoreCase("gh")
                && !command[1].equalsIgnoreCase("api")) {
            List<String> commands = new LinkedList<>(Arrays.stream(command).toList());
            commands.add("--repo");
            commands.add(repo);
            processedCommand = commands.toArray(new String[0]);
        }
        return processedCommand;
    }

}
