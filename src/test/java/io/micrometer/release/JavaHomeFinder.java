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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JavaHomeFinder {

    public static String findJavaExecutablePath() {
        return findJavaHomePath() + "/bin/java";
    }

    public static String findJavaHomePath() {
        if (isJavaAvailable("java")) {
            return System.getProperty("java.home");
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("which", "java");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    // /foo/bar/java/current/bin/java -> /foo/bar/java/current
                    return new File(line.trim()).getParentFile().getParent();
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Fallback to checking SDKMAN installation
        try {
            String userHome = System.getProperty("user.home");
            Path sdkmanJavaPath = Paths.get(userHome, ".sdkman", "candidates", "java", "current");
            if (Files.exists(sdkmanJavaPath)) {
                return sdkmanJavaPath.toAbsolutePath().toString();
            }
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        throw new UnsupportedOperationException("Java not found!!");
    }

    private static boolean isJavaAvailable(String javaCommand) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(javaCommand, "-version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        }
        catch (Exception e) {
            return false;
        }
    }

}
