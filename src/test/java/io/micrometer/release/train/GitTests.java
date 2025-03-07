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

import io.micrometer.release.common.ProcessRunner;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class GitTests {

    ProcessRunner processRunner = mock();

    @Test
    void should_checkout_tag() {
        Git git = new Git(processRunner);

        git.changeTag("v1.2.3");

        then(processRunner).should().run("git", "fetch", "origin", "refs/tags/v1.2.3");
        then(processRunner).should().run("git", "checkout", "FETCH_HEAD");
    }

    @Test
    void should_clone_a_repo() {
        Git git = new Git(processRunner);

        git.cloneRepo("foo", "micrometer-metrics/micrometer");

        then(processRunner).should()
            .run("gh", "repo", "clone", "micrometer-metrics/micrometer", "foo", "--", "-b", "foo", "--single-branch");
    }

}
