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
import io.micrometer.release.single.PostReleaseWorkflow;
import io.micrometer.release.train.ProjectTrainReleaseWorkflow;
import io.micrometer.release.train.TrainOptions;
import io.micrometer.release.train.TrainOptions.ProjectSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        // Env Vars

        // Single Project Post Release
        // GH_TOKEN
        // CHANGELOG_GENERATOR_VERSION
        // GITHUB_REPOSITORY
        // GITHUB_REF_NAME
        // PREVIOUS_REF_NAME
        // BSKY_IDENTIFIER
        // BSKY_HANDLE
        // SPRING_RELEASE_GCHAT_WEBHOOK_URL

        // Train Project Post Release
        // TRAIN_VERSIONS
        Main main = new Main();
        main.run();
    }

    void run() {
        ProcessRunner processRunner = new ProcessRunner();
        PostReleaseWorkflow postReleaseWorkflow = newPostReleaseWorkflow(processRunner);
        String githubOrgRepo = getGithubOrgRepository();
        String githubRefName = getGithubRefName();
        String previousRefName = getPreviousRefName();
        // TRAIN OPTIONS
        String contextPropVersions = getContextPropVersions();
        String micrometerVersions = getMicrometerVersions();
        String tracingVersions = getTracingVersions();
        String docsGenVersions = getDocsGenVersions();

        log.info("""
                @@@ MICROMETER RELEASE @@@

                Processing following env variables:

                GITHUB_REPOSITORY [%s]
                GITHUB_REF_NAME [%s]

                POST RELEASE TASKS:
                ------------------
                PREVIOUS_REF_NAME [%s]


                TRAIN OPTIONS:
                ------------------
                CONTEXT_PROPAGATION_VERSIONS [%s]
                MICROMETER_VERSIONS [%s]
                TRACING_VERSIONS [%s]
                DOCS_GEN_VERSIONS [%s]
                """.formatted(githubOrgRepo, githubRefName, previousRefName, contextPropVersions, micrometerVersions,
                tracingVersions, docsGenVersions));

        if (isTrainRelease(githubOrgRepo, contextPropVersions, micrometerVersions, tracingVersions, docsGenVersions)) {
            log.info("Will proceed with train release...");
            ProjectSetup projectSetup = new TrainOptions().parse(githubOrgRepo, contextPropVersions, micrometerVersions,
                    tracingVersions, docsGenVersions);
            trainReleaseWorkflow(githubOrgRepo, postReleaseWorkflow, processRunner).run(projectSetup);
        }
        else {
            log.info("Will proceed with single project post release workflow...");
            postReleaseWorkflow.run(githubOrgRepo, githubRefName, previousRefName);
        }
    }

    private boolean isTrainRelease(String githubOrgRepo, String contextPropVersions, String micrometerVersions,
            String tracingVersions, String docsGenVersions) {
        String modifiedGhOrgRepo = modifyForE2eTests(githubOrgRepo);
        return switch (modifiedGhOrgRepo) {
            case "micrometer-metrics/context-propagation" -> hasText(contextPropVersions);
            case "micrometer-metrics/micrometer" -> hasText(micrometerVersions);
            case "micrometer-metrics/tracing" -> hasText(tracingVersions);
            case "micrometer-metrics/micrometer-docs-generator" -> hasText(docsGenVersions);
            default -> false;
        };
    }

    // This is used for e2e tests
    private String modifyForE2eTests(String githubOrgRepo) {
        if ("marcingrzejszczak/gh-actions-test".equalsIgnoreCase(githubOrgRepo)) {
            return "micrometer-metrics/tracing";
        }
        return githubOrgRepo;
    }

    private boolean hasText(String text) {
        log.info("Checking if text is there [{}]", text);
        return text != null && !text.isBlank();
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

    ProjectTrainReleaseWorkflow trainReleaseWorkflow(String githubOrgRepo, PostReleaseWorkflow postReleaseWorkflow,
            ProcessRunner processRunner) {
        return new ProjectTrainReleaseWorkflow(githubOrgRepo, processRunner, postReleaseWorkflow);
    }

    PostReleaseWorkflow newPostReleaseWorkflow(ProcessRunner processRunner) {
        return new PostReleaseWorkflow(processRunner);
    }

}
