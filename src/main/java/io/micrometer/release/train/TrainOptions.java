/**
 * Copyright 2025 the original author or authors.
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
package io.micrometer.release.train;

import java.util.List;

public record TrainOptions() {

    enum ProjectName {

        CONTEXT_PROPAGATION("context-propagation"), MICROMETER("micrometer-bom"), TRACING("micrometer-tracing-bom"),
        DOC_GEN("micrometer-docs-generator");

        private final String groupId = "io.micrometer";

        private final String artifactId;

        ProjectName(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

    }

    public record Project(ProjectName projectName, List<Dependency> dependencies) {

        public static Project contextPropagation() {
            return new Project(ProjectName.CONTEXT_PROPAGATION, List.of());
        }

        public static Project micrometer(String contextPropagationVersion) {
            return new Project(ProjectName.MICROMETER, List
                .of(new Dependency(ProjectName.CONTEXT_PROPAGATION, versionOrNotSet(contextPropagationVersion))));
        }

        public static Project tracing(String contextPropagationVersion, String micrometerVersion) {
            return new Project(ProjectName.TRACING,
                    List.of(new Dependency(ProjectName.CONTEXT_PROPAGATION, versionOrNotSet(contextPropagationVersion)),
                            new Dependency(ProjectName.MICROMETER, versionOrNotSet(micrometerVersion))));
        }

        public static Project dosGen(String micrometerVersion, String tracingVersion) {
            return new Project(ProjectName.DOC_GEN,
                    List.of(new Dependency(ProjectName.MICROMETER, versionOrNotSet(micrometerVersion)),
                            new Dependency(ProjectName.TRACING, versionOrNotSet(tracingVersion))));
        }

        private static String versionOrNotSet(String contextPropagationVersion) {
            return contextPropagationVersion != null ? contextPropagationVersion : Dependency.NO_VERSION_SET;
        }

        public String name() {
            return projectName().name();
        }
    }

    public record Dependency(ProjectName projectName, String version) {

        public static final String NO_VERSION_SET = "";

        public boolean isVersionSet() {
            return NO_VERSION_SET.equals(version);
        }
    }

}
