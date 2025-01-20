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

public class Main {

    public static void main(String[] args) throws Exception {
        newWorkflow().run();
    }

    private static PostReleaseWorkflow newWorkflow() {
        return new PostReleaseWorkflow(new ChangelogGeneratorDownloader(), new ChangelogGenerator(),
                new ChangelogFetcher(), new ChangelogProcessor(), new ReleaseNotesUpdater(), new MilestoneUpdater(),
                new NotificationSender());
    }

}
