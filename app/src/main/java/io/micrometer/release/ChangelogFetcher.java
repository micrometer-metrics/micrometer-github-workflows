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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

class ChangelogFetcher {

    private static final Logger log = LoggerFactory.getLogger(ChangelogFetcher.class);

    static final String OUTPUT_FILE = "old_changelog.md";

    private final File outputFile;

    private final ProcessRunner processRunner;

    public ChangelogFetcher() {
        this.outputFile = new File(OUTPUT_FILE);
        this.processRunner = new ProcessRunner();
    }

    // for tests
    ChangelogFetcher(File outputFile, ProcessRunner processRunner) {
        this.outputFile = outputFile;
        this.processRunner = processRunner;
    }

    File fetchChangelog(String githubRefName, String githubOrgRepo) {
        log.info("Fetching changelog for [{}]...", githubRefName);
        // TODO: Move to a util
        String orgName = githubOrgRepo.contains("/") ? githubOrgRepo.split("/")[0] : githubOrgRepo;
        String repoName = githubOrgRepo.contains("/") ? githubOrgRepo.split("/")[1] : githubOrgRepo;
        processRunner.run("sh", "-c", String.format("gh release view %s --repo %s/%s --json body --jq .body > %s",
                githubRefName, orgName, repoName, outputFile.getAbsolutePath()));
        return outputFile;
    }

}
