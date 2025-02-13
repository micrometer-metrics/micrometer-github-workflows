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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class FutureUtility {

    static void waitForTasksToComplete(List<CompletableFuture<Void>> releaseTasks) {
        CompletableFuture
            .allOf(releaseTasks.stream()
                .map(future -> future.orTimeout(20, TimeUnit.MINUTES))
                .toList()
                .toArray(new CompletableFuture[0]))
            .join();
    }

}
