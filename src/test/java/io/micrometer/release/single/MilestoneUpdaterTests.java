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
package io.micrometer.release.single;

import io.micrometer.release.common.ProcessRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MilestoneUpdaterTests {

    @Test
    void should_call_gh_api_to_close_a_milestone() {
        ProcessRunner processRunner = mock();
        String ghRepo = "micrometer-metrics/micrometer";
        given(processRunner.getOrgRepo()).willReturn(ghRepo);
        given(processRunner.run("gh", "api", "--paginate", "/repos/micrometer-metrics/micrometer/milestones", "--jq",
                String.format(".[] | select(.title == \"%s\") | {number: .number, title: .title}", "1.2.3")))
            .willReturn(List.of("{\"number\":100,\"title\":\"1.2.3\"}"));
        MilestoneUpdater milestoneUpdater = new MilestoneUpdater(processRunner, ghRepo,
                new MilestoneMigrator(processRunner, new MilestoneIssueReassigner(processRunner)));

        milestoneUpdater.closeMilestone("v1.2.3");

        verify(processRunner).run("gh", "api", "-X", "PATCH", "/repos/micrometer-metrics/micrometer/milestones/100",
                "-f", "state=closed");
    }

    @Test
    void should_update_milestones() {
        MilestoneMigrator migrator = mock();
        MilestoneUpdater milestoneUpdater = new MilestoneUpdater(null, null, migrator);

        milestoneUpdater.updateMilestones("v1.2.3");

        verify(migrator).migrateMilestones("v1.2.3");
    }

}
