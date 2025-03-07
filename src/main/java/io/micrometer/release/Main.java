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

import io.micrometer.release.common.Input;
import io.micrometer.release.common.ProcessRunner;
import io.micrometer.release.meta.MetaTrainReleaseWorkflow;
import io.micrometer.release.single.PostReleaseWorkflow;
import io.micrometer.release.train.ProjectTrainReleaseWorkflow;
import io.micrometer.release.train.TrainOptions;
import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.run();
    }

    void run() {
        String githubOrgRepo = getGithubOrgRepository();
        String githubRefName = getGithubRefName();
        String previousRefName = getPreviousRefName();
        boolean testMode = Boolean.parseBoolean(getTestMode());
        // TRAIN OPTIONS
        String contextPropVersions = getContextPropVersions();
        String micrometerVersions = getMicrometerVersions();
        String tracingVersions = getTracingVersions();
        String docsGenVersions = getDocsGenVersions();
        // META RELEASE OPTIONS
        boolean metaReleaseEnabled = Boolean.parseBoolean(getMetaReleaseEnabled());

        log.info("""
                @@@ MICROMETER RELEASE @@@

                Processing following env variables:

                ====================================
                COMMON:
                -----------------
                GITHUB_REPOSITORY [%s]
                GITHUB_REF_NAME [%s]
                TEST_MODE [%s]
                ====================================

                ====================================
                POST RELEASE TASKS:
                ------------------
                PREVIOUS_REF_NAME [%s]
                ====================================

                ====================================
                TRAIN OPTIONS:
                ------------------
                CONTEXT_PROPAGATION_VERSIONS [%s]
                MICROMETER_VERSIONS [%s]
                TRACING_VERSIONS [%s]
                DOCS_GEN_VERSIONS [%s]
                ====================================

                ====================================
                META RELEASE OPTIONS:
                ------------------
                META_RELEASE_ENABLED [%s]
                ====================================
                """.formatted(githubOrgRepo, githubRefName, testMode, previousRefName, contextPropVersions,
                micrometerVersions, tracingVersions, docsGenVersions, metaReleaseEnabled));

        ProcessRunner processRunner = new ProcessRunner(githubOrgRepo);
        PostReleaseWorkflow postReleaseWorkflow = newPostReleaseWorkflow(processRunner);

        if (isMetaRelease(processRunner.getOrgRepo(), contextPropVersions, micrometerVersions, tracingVersions,
                docsGenVersions, metaReleaseEnabled)) {
            log.info("Will proceed with meta release...");
            metaReleaseWorkflow(postReleaseWorkflow).run(parseMetaTrainOptions(testMode, contextPropVersions,
                    micrometerVersions, tracingVersions, docsGenVersions));
        }
        else if (!metaReleaseEnabled && isTrainRelease(processRunner.getOrgRepo(), contextPropVersions,
                micrometerVersions, tracingVersions, docsGenVersions)) {
            log.info("Will proceed with train release for a single project [{}]...", processRunner.getOrgRepo());
            ProjectSetup projectSetup = parseTrainOptions(testMode, processRunner.getOrgRepo(), contextPropVersions,
                    micrometerVersions, tracingVersions, docsGenVersions);
            trainReleaseWorkflow(postReleaseWorkflow, processRunner).run(projectSetup);
        }
        else {
            log.info("Will proceed with single project [{}] post release workflow...", processRunner.getOrgRepo());
            postReleaseWorkflow.run(githubRefName, previousRefName);
        }
    }

    MetaTrainReleaseWorkflow metaReleaseWorkflow(PostReleaseWorkflow postReleaseWorkflow) {
        return new MetaTrainReleaseWorkflow(postReleaseWorkflow);
    }

    private List<ProjectSetup> parseMetaTrainOptions(boolean testMode, String contextPropVersions,
            String micrometerVersions, String tracingVersions, String docsGenVersions) {
        List<ProjectSetup> projectSetups = TrainOptions.withTestMode(testMode)
            .parseForMetaTrain(contextPropVersions, micrometerVersions, tracingVersions, docsGenVersions);
        log.info("Parsed options for meta-release {}", projectSetups);
        return projectSetups;
    }

    private ProjectSetup parseTrainOptions(boolean testMode, String githubOrgRepo, String contextPropVersions,
            String micrometerVersions, String tracingVersions, String docsGenVersions) {
        ProjectSetup setup = TrainOptions.withTestMode(testMode)
            .parseForSingleProjectTrain(githubOrgRepo, contextPropVersions, micrometerVersions, tracingVersions,
                    docsGenVersions);
        log.info("Parsed options for meta-release [{}]", setup);
        return setup;
    }

    private boolean isMetaRelease(String githubOrgRepo, String contextPropVersions, String micrometerVersions,
            String tracingVersions, String docsGenVersions, boolean metaReleaseEnabled) {
        return metaReleaseEnabled && isTrainRelease(githubOrgRepo, contextPropVersions, micrometerVersions,
                tracingVersions, docsGenVersions);
    }

    private boolean isTrainRelease(String githubOrgRepo, String contextPropVersions, String micrometerVersions,
            String tracingVersions, String docsGenVersions) {
        return switch (githubOrgRepo) {
            case "micrometer-metrics/context-propagation" -> hasText(contextPropVersions);
            case "micrometer-metrics/micrometer", "marcingrzejszczak/gh-actions-test" -> hasText(micrometerVersions);
            case "micrometer-metrics/tracing", "marcingrzejszczak/gh-actions-test2" -> hasText(tracingVersions);
            case "micrometer-metrics/micrometer-docs-generator" -> hasText(docsGenVersions);
            default -> false;
        };
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    String getTestMode() {
        return Input.getTestMode();
    }

    String getDocsGenVersions() {
        return Input.getDocsGenVersions();
    }

    String getTracingVersions() {
        return Input.getTracingVersions();
    }

    String getMicrometerVersions() {
        return Input.getMicrometerVersions();
    }

    String getContextPropVersions() {
        return Input.getContextPropagationVersions();
    }

    String getGithubOrgRepository() {
        return Input.getGithubOrgRepository();
    }

    String getPreviousRefName() {
        return Input.getPreviousRefName();
    }

    String getGithubRefName() {
        return Input.getGithubRefName();
    }

    String getMetaReleaseEnabled() {
        return Input.getMetaReleaseEnabled();
    }

    ProjectTrainReleaseWorkflow trainReleaseWorkflow(PostReleaseWorkflow postReleaseWorkflow,
            ProcessRunner processRunner) {
        return new ProjectTrainReleaseWorkflow(processRunner, postReleaseWorkflow);
    }

    PostReleaseWorkflow newPostReleaseWorkflow(ProcessRunner processRunner) {
        return new PostReleaseWorkflow(processRunner);
    }

}
