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

import java.io.IOException;

class MilestoneUpdater {

    void closeMilestone() throws IOException, InterruptedException {
        System.out.println("Closing milestone...");
        String milestoneName = System.getenv("GITHUB_REF_NAME").replace("v", "");
        Process process = new ProcessBuilder("gh", "api",
                "/repos/" + System.getenv("GITHUB_REPOSITORY") + "/milestones?state=open", "--jq",
                "\".[] | select(.title == \\\"" + milestoneName + "\\\").number\"")
            .inheritIO()
            .start();
        if (process.waitFor() != 0) {
            throw new RuntimeException("Failed to fetch milestone ID");
        }
    }

}
