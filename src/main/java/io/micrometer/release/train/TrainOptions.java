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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.release.train.TrainOptions.Project.*;

public class TrainOptions {

    private final boolean testMode;

    private TrainOptions(boolean testMode) {
        this.testMode = testMode;
    }

    public static TrainOptions withTestMode(boolean testMode) {
        return new TrainOptions(testMode);
    }

    public ProjectSetup parseForSingleProjectTrain(String ghOrgRepo, String contextPropagationVersions,
            String micrometerVersions, String tracingVersions, String docsGenVersions) {
        SplitProjects splitProjects = toSplitProjects(contextPropagationVersions, micrometerVersions, tracingVersions,
                docsGenVersions);
        return projectSetup(ghOrgRepo, splitProjects);
    }

    public List<ProjectSetup> parseForMetaTrain(String contextPropagationVersions, String micrometerVersions,
            String tracingVersions, String docsGenVersions) {
        SplitProjects splitProjects = toSplitProjects(contextPropagationVersions, micrometerVersions, tracingVersions,
                docsGenVersions);
        List<ProjectWithDependencies> projects = projectWithDependencies(splitProjects);
        Map<ProjectDefinition, List<ProjectWithDependencies>> perProject = projects.stream()
            .collect(Collectors.groupingBy(p -> p.project.projectDefinition));
        return perProject.entrySet()
            .stream()
            .map(e -> new ProjectSetup(e.getValue(), e.getKey().orgRepo))
            .sorted(Comparator.comparingInt(o -> o.thisProject.get(0).project.projectDefinition.ordinal())) // ORDER
                                                                                                            // MATTERS!!!!
            .toList();
    }

    private SplitProjects toSplitProjects(String contextPropagationVersions, String micrometerVersions,
            String tracingVersions, String docsGenVersions) {
        assertAtLeastOneVersionSet(contextPropagationVersions, micrometerVersions, tracingVersions, docsGenVersions);
        String[] contextPropagationVersion = split(contextPropagationVersions);
        String[] micrometerVersion = split(micrometerVersions);
        String[] tracingVersion = split(tracingVersions);
        String[] docsGenVersion = split(docsGenVersions);
        assertSameSizeOrEmpty(contextPropagationVersion, micrometerVersion, tracingVersion, docsGenVersion);
        return new SplitProjects(contextPropagationVersion, micrometerVersion, tracingVersion, docsGenVersion);
    }

    private void assertAtLeastOneVersionSet(String contextPropagationVersions, String micrometerVersions,
            String tracingVersions, String docsGenVersions) {
        if (!hasText(contextPropagationVersions) && !hasText(micrometerVersions) && !hasText(tracingVersions)
                && !hasText(docsGenVersions)) {
            throw new IllegalStateException("At least one of the versions must be set...");
        }
    }

    private ProjectSetup projectSetup(String ghOrgRepo, SplitProjects splitProjects) {
        List<ProjectWithDependencies> projects = projectWithDependencies(splitProjects);
        return new ProjectSetup(projects, ghOrgRepo);
    }

    private List<ProjectWithDependencies> projectWithDependencies(SplitProjects splitProjects) {
        List<ProjectWithDependencies> projects = new ArrayList<>();
        parseContextPropagation(splitProjects.contextP, projects);
        parseMicrometer(splitProjects.micrometer, projects, splitProjects.contextP);
        parseTracing(splitProjects.tracing, splitProjects.contextP, splitProjects.micrometer, projects);
        parseDocsGen(splitProjects.docsGen, splitProjects.tracing, splitProjects.micrometer, projects);
        return projects;
    }

    private void assertSameSizeOrEmpty(String[] contextP, String[] micrometer, String[] tracing, String[] docsGen) {
        List<Integer> sizes = Stream.of(contextP.length, micrometer.length, tracing.length, docsGen.length).toList();
        int largestSize = sizes.stream().max(Integer::compareTo).orElse(0);
        // The versions need to be either NOT set or of the same size. E.g:
        // GOOD: context propagation: "", micrometer: "1.0.0,2.0.0,3.0.0", tracing:
        // "4.0.0,5.0.0,6.0.0", docsGen: ""
        // BAD: context propagation: "", micrometer: "1.0.0", tracing: "4.0.0,5.0.0",
        // docsGen: "7.0.0,8.0.0,9.0.0"
        boolean sizeSameOrZero = sizes.stream().allMatch(integer -> integer == largestSize || integer == 0);
        if (!sizeSameOrZero) {
            throw new IllegalStateException(
                    "Project versions need to be of the same size or be empty. Provided setup:\n"
                            + "\t[Context Propagation] versions size [%s], values [%s]%n".formatted(contextP.length,
                                    String.join(",", contextP))
                            + "\t[Micrometer] versions size [%s], values [%s]%n".formatted(micrometer.length,
                                    String.join(",", micrometer))
                            + "\t[Tracing] versions size [%s], values [%s]%n".formatted(tracing.length,
                                    String.join(",", tracing))
                            + "\t[Docs Gen] versions size [%s], values [%s]%n".formatted(docsGen.length,
                                    String.join(",", docsGen)));
        }
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
            Tracing tracingWithDeps = tracing.with(contextPropagation, micrometer);
            projects.add(tracingWithDeps);
            if (testMode) {
                projects.add(new GhActionsTracingTest(tracingWithDeps));
            }
        }
    }

    private void parseMicrometer(String[] micrometerVersion, List<ProjectWithDependencies> projects,
            String[] contextPropagationVersion) {
        for (int i = 0; i < micrometerVersion.length; i++) {
            MicrometerProject micrometer = micrometer(micrometerVersion[i]);
            Micrometer micrometerWithDeps;
            if (contextPropagationVersion.length > 0) {
                micrometerWithDeps = micrometer.withContextPropagation(contextPropagationVersion[i]);
                projects.add(micrometerWithDeps);
            }
            else {
                micrometerWithDeps = micrometer.noContextPropagation();
                projects.add(micrometerWithDeps);
            }
            if (testMode) {
                projects.add(new GhActionsTest(micrometerWithDeps));
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
        return versions.split(",");
    }

    private static boolean hasText(String contextPropagationVersions) {
        return contextPropagationVersions != null && !contextPropagationVersions.isEmpty();
    }

    enum ProjectDefinition {

        TEST("gh-actions-test", "micrometer-bom"), TRACING_TEST("gh-actions-test2", "micrometer-tracing-bom"),
        CONTEXT_PROPAGATION("context-propagation", "context-propagation"), MICROMETER("micrometer", "micrometer-bom"),
        TRACING("tracing", "micrometer-tracing-bom"), DOC_GEN("micrometer-docs-generator", "micrometer-docs-generator");

        private final String groupId = "io.micrometer";

        private final String orgRepo;

        private final String artifactId;

        ProjectDefinition(String repository, String artifactId) {
            this.orgRepo = repository.contains("gh-actions-test") ? "marcingrzejszczak/" + repository
                    : "micrometer-metrics/" + repository;
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

        public String ghRepo() {
            return ghOrgRepo().split("/")[1];
        }

        public String ghOrgRepo() {
            return this.thisProject.get(0).project.projectDefinition.orgRepo;
        }

        public String artifactToCheck() {
            return this.thisProject.get(0).project.projectDefinition.artifactId;
        }

        public Set<io.micrometer.release.common.Dependency> expectedDependencies() {
            return this.thisProject.stream()
                .flatMap(p -> p.getDependencies().stream())
                .map(dependency -> new io.micrometer.release.common.Dependency(dependency.projectDefinition.groupId,
                        dependency.projectDefinition.artifactId, dependency.version, false))
                .collect(Collectors.toSet());
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

    static class GhActionsTest extends ProjectWithDependencies {

        public GhActionsTest(Micrometer micrometer) {
            super(new Project(ProjectDefinition.TEST, micrometer.getProject().projectVersion),
                    micrometer.getDependencies());
        }

    }

    static class GhActionsTracingTest extends ProjectWithDependencies {

        public GhActionsTracingTest(Tracing tracing) {
            super(new Project(ProjectDefinition.TEST, tracing.getProject().projectVersion), tracing.getDependencies());
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

    record SplitProjects(String[] contextP, String[] micrometer, String[] tracing, String[] docsGen) {

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SplitProjects that = (SplitProjects) o;
            return Objects.deepEquals(tracing, that.tracing) && Objects.deepEquals(docsGen, that.docsGen)
                    && Objects.deepEquals(contextP, that.contextP) && Objects.deepEquals(micrometer, that.micrometer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(contextP), Arrays.hashCode(micrometer), Arrays.hashCode(tracing),
                    Arrays.hashCode(docsGen));
        }
    }

}
