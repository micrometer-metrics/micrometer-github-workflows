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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class MilestoneUpdaterTests {

    @Test
    void should_call_gh_api_to_close_a_milestone() {
        ProcessRunner processRunner = mock();
        String ghRepo = "micrometer-metrics";
        MilestoneUpdater milestoneUpdater = new MilestoneUpdater(processRunner, ghRepo,
                new MilestoneMigrator(processRunner, ghRepo, new MilestoneIssueReassigner(processRunner, ghRepo)));

        milestoneUpdater.closeMilestone("v1.2.3");

        verify(processRunner).run("gh", "api", "/repos/micrometer-metrics/milestones?state=open", "--jq",
                "\".[] | select(.title == \\\"1.2.3\\\").number\"");
    }

}
