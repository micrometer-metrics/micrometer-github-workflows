/**
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.micrometer.release.train;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.micrometer.release.train.TrainOptions.Project.*;

public class TrainOptions {

    public ProjectSetup parse(String ghOrgRepo, String contextPropagationVersions, String micrometerVersions,
            String tracingVersions, String docsGenVersions) {
        List<ProjectWithDependencies> projects = new ArrayList<>();
        String[] contextPropagationVersion = split(contextPropagationVersions);
        parseContextPropagation(contextPropagationVersion, projects);
        String[] micrometerVersion = split(micrometerVersions);
        parseMicrometer(micrometerVersion, projects, contextPropagationVersion);
        String[] tracingVersion = split(tracingVersions);
        parseTracing(tracingVersion, contextPropagationVersion, micrometerVersion, projects);
        String[] docsGenVersion = split(docsGenVersions);
        parseDocsGen(docsGenVersion, tracingVersion, micrometerVersion, projects);
        return new ProjectSetup(projects, ghOrgRepo);
    }

    private void parseDocsGen(String[] docsGenVersion, String[] tracingVersion, String[] micrometerVersion,
            List<ProjectWithDependencies> projects) {
        for (int i = 0; i < docsGenVersion.length; i++) {
            DocsGenProject docsGen = docsGenProject(docsGenVersion[i]);
            String micrometer = micrometerVersion.length > 0 ? micrometerVersion[i] : null;
            String tracing = tracingVersion.length > 0 ? tracingVersion[i] : null;
            projects.add(docsGen.with(micrometer, tracing));
        }
    }

    private void parseTracing(String[] tracingVersion, String[] contextPropagationVersion, String[] micrometerVersion,
            List<ProjectWithDependencies> projects) {
        for (int i = 0; i < tracingVersion.length; i++) {
            TracingProject tracing = tracing(tracingVersion[i]);
            String contextPropagation = contextPropagationVersion.length > 0 ? contextPropagationVersion[i] : null;
            String micrometer = micrometerVersion.length > 0 ? micrometerVersion[i] : null;
            projects.add(tracing.with(contextPropagation, micrometer));
        }
    }

    private void parseMicrometer(String[] micrometerVersion, List<ProjectWithDependencies> projects,
            String[] contextPropagationVersion) {
        for (int i = 0; i < micrometerVersion.length; i++) {
            MicrometerProject micrometer = micrometer(micrometerVersion[i]);
            if (contextPropagationVersion.length > 0) {
                projects.add(micrometer.withContextPropagation(contextPropagationVersion[i]));
            }
            else {
                projects.add(micrometer.noContextPropagation());
            }
        }
    }

    private void parseContextPropagation(String[] contextPropagationVersion, List<ProjectWithDependencies> projects) {
        for (String version : contextPropagationVersion) {
            projects.add(contextPropagation(version));
        }
    }

    private String[] split(String versions) {
        if (versions == null || versions.isEmpty()) {
            return new String[0];
        }
        String[] split = versions.split(",");
        if (split.length != 3) { // TODO: We're releasing 3 branches at the same time
            throw new IllegalStateException("We're releasing 3 branches at the same time!");
        }
        return split;
    }

    private static boolean hasText(String contextPropagationVersions) {
        return contextPropagationVersions != null && !contextPropagationVersions.isEmpty();
    }

    enum ProjectDefinition {

        CONTEXT_PROPAGATION("context-propagation", "context-propagation"), MICROMETER("micrometer", "micrometer-bom"),
        TRACING("tracing", "micrometer-tracing-bom"), DOC_GEN("micrometer-docs-generator", "micrometer-docs-generator");

        private final String groupId = "io.micrometer";

        private final String orgRepo;

        private final String artifactId;

        ProjectDefinition(String repository, String artifactId) {
            this.orgRepo = "micrometer-metrics/" + repository;
            this.artifactId = artifactId;
        }

        @Override
        public String toString() {
            return "ProjectDefinition{" + "groupId='" + groupId + '\'' + ", orgRepo='" + orgRepo + '\''
                    + ", artifactId='" + artifactId + '\'' + '}';
        }

    }

    public static class ProjectSetup {

        private final List<ProjectWithDependencies> thisProject;

        // TODO: We will use the projects when we will start doing meta-release
        ProjectSetup(List<ProjectWithDependencies> projects, String orgRepo) {
            this.thisProject = projects.stream()
                .filter(p -> p.project.projectDefinition.orgRepo.equalsIgnoreCase(orgRepo))
                .toList();
            if (this.thisProject.isEmpty()) {
                throw new IllegalStateException("There is no project with org repo " + orgRepo);
            }
        }

        public List<String> versionsForThisProject() {
            return this.thisProject.stream().map(p -> p.getProject().getProjectVersion()).toList();
        }

        public String artifactToCheck() {
            return this.thisProject.get(0).project.projectDefinition.artifactId;
        }

        public List<io.micrometer.release.common.Dependency> expectedDependencies() {
            return this.thisProject.stream()
                .flatMap(p -> p.getDependencies().stream())
                .map(dependency -> new io.micrometer.release.common.Dependency(dependency.projectDefinition.groupId,
                        dependency.projectDefinition.artifactId, dependency.version, false))
                .toList();
        }

        @Override
        public String toString() {
            return "ProjectSetup{" + "\nthisProject=" + thisProject + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProjectSetup that = (ProjectSetup) o;
            return Objects.equals(thisProject, that.thisProject);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(thisProject);
        }

    }

    static class ProjectWithDependencies {

        private final Project project;

        private final List<Dependency> dependencies;

        public ProjectWithDependencies(Project project, List<Dependency> dependencies) {
            this.project = project;
            this.dependencies = dependencies;
        }

        public Project getProject() {
            return project;
        }

        public List<Dependency> getDependencies() {
            return dependencies;
        }

        @Override
        public String toString() {
            return "ProjectWithDependencies{" + "\nproject=" + project + "\n, dependencies=" + dependencies + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProjectWithDependencies that = (ProjectWithDependencies) o;
            return Objects.equals(project, that.project) && Objects.equals(dependencies, that.dependencies);
        }

        @Override
        public int hashCode() {
            return Objects.hash(project, dependencies);
        }

    }

    static class ContextPropagation extends ProjectWithDependencies {

        public ContextPropagation(String version) {
            super(new Project(ProjectDefinition.CONTEXT_PROPAGATION, version), List.of());
        }

    }

    static class MicrometerProject extends Project {

        MicrometerProject(String version) {
            super(ProjectDefinition.MICROMETER, version);
        }

        public Micrometer withContextPropagation(String version) {
            return new Micrometer(getProjectVersion(), version);
        }

        public Micrometer noContextPropagation() {
            return new Micrometer(getProjectVersion(), null);
        }

    }

    static class Micrometer extends ProjectWithDependencies {

        public Micrometer(String version, String contextPropagationVersion) {
            super(new Project(ProjectDefinition.MICROMETER, version),
                    contextPropagationVersion != null
                            ? List.of(new Dependency(ProjectDefinition.CONTEXT_PROPAGATION, contextPropagationVersion))
                            : List.of());
        }

    }

    static class TracingProject extends Project {

        TracingProject(String version) {
            super(ProjectDefinition.TRACING, version);
        }

        Tracing with(String contextPropagationVersion, String micrometerVersion) {
            return new Tracing(getProjectVersion(), contextPropagationVersion, micrometerVersion);
        }

    }

    static class Tracing extends ProjectWithDependencies {

        Tracing(String version, String micrometerVersion, String contextPropagationVersion) {
            super(new Project(ProjectDefinition.TRACING, version),
                    dependencies(micrometerVersion, contextPropagationVersion));
        }

        private static List<Dependency> dependencies(String micrometerVersion, String contextPropagationVersion) {
            List<Dependency> dependencies = new ArrayList<>();
            if (hasText(micrometerVersion)) {
                dependencies.add(new Dependency(ProjectDefinition.MICROMETER, micrometerVersion));
            }
            if (hasText(contextPropagationVersion)) {
                dependencies.add(new Dependency(ProjectDefinition.CONTEXT_PROPAGATION, contextPropagationVersion));
            }
            return dependencies;
        }

    }

    static class DocsGenProject extends Project {

        DocsGenProject(String version) {
            super(ProjectDefinition.DOC_GEN, version);
        }

        DocsGen with(String micrometerVersion, String tracingVersion) {
            return new DocsGen(getProjectVersion(), tracingVersion, micrometerVersion);
        }

    }

    static class DocsGen extends ProjectWithDependencies {

        DocsGen(String version, String micrometerVersion, String tracingVersion) {
            super(new Project(ProjectDefinition.DOC_GEN, version), dependencies(micrometerVersion, tracingVersion));
        }

        private static List<Dependency> dependencies(String micrometerVersion, String tracingVersion) {
            List<Dependency> dependencies = new ArrayList<>();
            if (hasText(micrometerVersion)) {
                dependencies.add(new Dependency(ProjectDefinition.MICROMETER, micrometerVersion));
            }
            if (hasText(tracingVersion)) {
                dependencies.add(new Dependency(ProjectDefinition.TRACING, tracingVersion));
            }
            return dependencies;
        }

    }

    static class Project {

        private final ProjectDefinition projectDefinition;

        private final String projectVersion;

        Project(ProjectDefinition projectDefinition, String projectVersion) {
            this.projectDefinition = projectDefinition;
            this.projectVersion = projectVersion;
        }

        static ContextPropagation contextPropagation(String version) {
            return new ContextPropagation(version);
        }

        static MicrometerProject micrometer(String micrometerVersion) {
            return new MicrometerProject(micrometerVersion);
        }

        static TracingProject tracing(String tracingVersion) {
            return new TracingProject(tracingVersion);
        }

        static DocsGenProject docsGenProject(String version) {
            return new DocsGenProject(version);
        }

        String getProjectVersion() {
            return projectVersion;
        }

        @Override
        public String toString() {
            return "Project{" + "\nprojectDefinition=" + projectDefinition + ", \nprojectVersion='" + projectVersion
                    + '\'' + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Project project = (Project) o;
            return projectDefinition == project.projectDefinition
                    && Objects.equals(projectVersion, project.projectVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectDefinition, projectVersion);
        }

    }

    record Dependency(ProjectDefinition projectDefinition, String version) {

    }

}
