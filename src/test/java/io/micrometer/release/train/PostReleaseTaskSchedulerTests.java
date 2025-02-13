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

import io.micrometer.release.single.PostReleaseWorkflow;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class PostReleaseTaskSchedulerTests {

    PostReleaseWorkflow postReleaseWorkflow = mock();

    PostReleaseTaskScheduler scheduler = new PostReleaseTaskScheduler(postReleaseWorkflow,
            "micrometer-metrics/micrometer");

    @Test
    void should_schedule_release_tasks() {
        scheduler.runPostReleaseTasks(List.of("1.0.0", "1.1.0", "1.2.0"));

        InOrder inOrder = inOrder(postReleaseWorkflow);
        inOrder.verify(postReleaseWorkflow).run("micrometer-metrics/micrometer", "v1.0.0", null);
        inOrder.verify(postReleaseWorkflow).run("micrometer-metrics/micrometer", "v1.1.0", "v1.0.0");
        inOrder.verify(postReleaseWorkflow).run("micrometer-metrics/micrometer", "v1.2.0", "v1.1.0");
    }

}
