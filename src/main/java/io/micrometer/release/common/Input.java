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

public class Input {

    public static void assertInputs(String githubOrgRepo, String githubRefName, String previousRefName) {
        if (githubOrgRepo == null) {
            throw new IllegalStateException("No repo found, please provide the GITHUB_REPOSITORY env variable");
        }
        if (githubRefName == null) {
            throw new IllegalStateException("No github ref found, please provide the GITHUB_REF_NAME env variable");
        }
        if (!githubRefName.startsWith("v")) {
            throw new IllegalStateException("Github ref must be a tag (must start with 'v'): " + githubRefName);
        }
        if (previousRefName != null && !previousRefName.isBlank() && !previousRefName.startsWith("v")) {
            throw new IllegalStateException(
                    "Previous github ref must be a tag (must start with 'v'): " + previousRefName);
        }
    }

    public static String getGithubOrgRepository() {
        return System.getenv("GITHUB_REPOSITORY");
    }

    public static String getPreviousRefName() {
        return System.getenv("PREVIOUS_REF_NAME");
    }

    public static String getGithubRefName() {
        return System.getenv("GITHUB_REF_NAME");
    }

    public static String getTrainVersions() {
        return System.getenv("TRAIN_VERSIONS");
    }

    public static String getContextPropagationVersions() {
        return System.getenv("CONTEXT_PROPAGATION_VERSIONS");
    }

    public static String getMicrometerVersions() {
        return System.getenv("MICROMETER_VERSIONS");
    }

    public static String getTracingVersions() {
        return System.getenv("TRACING_VERSIONS");
    }

    public static String getDocsGenVersions() {
        return System.getenv("DOCS_GEN_VERSIONS");
    }

    public static String getGhToken() {
        return System.getenv("GH_TOKEN");
    }

}
