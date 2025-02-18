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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PostReleaseTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(PostReleaseTaskScheduler.class);

    private final PostReleaseWorkflow postReleaseWorkflow;

    private final Git git;

    private final String githubOrgRepo;

    PostReleaseTaskScheduler(PostReleaseWorkflow postReleaseWorkflow, Git git, String githubOrgRepo) {
        this.postReleaseWorkflow = postReleaseWorkflow;
        this.git = git;
        this.githubOrgRepo = githubOrgRepo;
    }

    void runPostReleaseTasks(List<String> versions) {
        List<String> sortedVersions = new ArrayList<>(versions);
        sortedVersions.sort(Comparator.comparing(PostReleaseTaskScheduler::extractMajorMinorVersion));
        String previousVersion = null;
        for (String version : sortedVersions) {
            git.changeTag("v" + version);
            log.info("Running post release task for version [{}] and previous version [{}]", version, previousVersion);
            postReleaseWorkflow.run(githubOrgRepo, "v" + version,
                    previousVersion != null ? ("v" + previousVersion) : null);
            previousVersion = version;
        }
        log.info("All post-release actions completed!");
    }

    private static String extractMajorMinorVersion(String version) {
        return version.substring(0, version.lastIndexOf('.'));
    }

}
