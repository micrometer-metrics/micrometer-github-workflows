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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Git {

    private static final Logger log = LoggerFactory.getLogger(Git.class);

    private final ProcessRunner processRunner;

    Git(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    void changeTag(String tag) {
        log.info("Changing git head to tag [{}]", tag);
        processRunner.run("git", "fetch", "origin", "refs/tags/" + tag);
        processRunner.run("git", "checkout", "FETCH_HEAD");
    }

}
