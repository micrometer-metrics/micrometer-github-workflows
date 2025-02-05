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

class MainTests {

    public static void main(String[] args) throws Exception {
        // Env Vars
        // GH_TOKEN
        // CHANGELOG_GENERATOR_VERSION
        // GITHUB_REPOSITORY
        // GITHUB_REF_NAME
        // PREVIOUS_REF_NAME
        newWorkflow().run();
    }

    private static PostReleaseWorkflow newWorkflow() {
        ProcessRunner processRunner = new ProcessRunner(System.getenv("GITHUB_REPOSITORY"));
        return new PostReleaseWorkflow(new ChangelogGeneratorDownloader(),
                ChangelogGeneratorTests.testChangelogGenerator(), new ChangelogFetcher(processRunner),
                ChangelogProcessorTests.testChangelogProcessor(), new ReleaseNotesUpdater(processRunner),
                new MilestoneUpdater(processRunner), new NotificationSender());
    }

}
