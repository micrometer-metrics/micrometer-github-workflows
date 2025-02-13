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

import static org.assertj.core.api.BDDAssertions.then;

import java.util.List;

import java.util.concurrent.CompletableFuture;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class FutureUtilityTests {

    @Test
    void should_join_futures() {
        AtomicInteger counter = new AtomicInteger();

        FutureUtility.waitForTasksToComplete(List.of(CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100); // to ensure that we wait
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            counter.incrementAndGet();
        }), CompletableFuture.runAsync(counter::incrementAndGet)));

        then(counter).hasValue(2);
    }

}
