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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class VersionToBranchConverter {

    private static final Logger log = LoggerFactory.getLogger(VersionToBranchConverter.class);

    private final String githubToken;

    private final String githubApiBranchUrl;

    private final HttpClient httpClient;

    VersionToBranchConverter(String githubToken, String githubApiBranchUrl, HttpClient httpClient) {
        this.githubToken = githubToken;
        this.githubApiBranchUrl = githubApiBranchUrl;
        this.httpClient = httpClient;
    }

    Map<String, String> convert(List<String> versions) {
        Map<String, String> versionsToBranches = new HashMap<>();
        try {
            for (String version : versions) {
                String branch = branchOrMainIfBranchMissing(version);
                versionsToBranches.put(version, branch);
            }
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
        log.info("Matched versions to branches {}", versionsToBranches);
        return versionsToBranches;
    }

    private String branchOrMainIfBranchMissing(String version) throws IOException, InterruptedException {
        log.info("Will determine what branch to search for for version [{}]", version);
        // 1.2.3-M2 -> 1.2
        String majorMinor = version.substring(0, version.lastIndexOf('.'));
        String potentialBranch = majorMinor + ".x";
        if (doesBranchExist(potentialBranch)) {
            log.info("Branch [{}] exists", potentialBranch);
            return potentialBranch;
        }
        else {
            log.info("Branch [{}] does not exist. Defaulting to 'main'.", potentialBranch);
            return "main";
        }
    }

    private boolean doesBranchExist(String branch) throws IOException, InterruptedException {
        String branchUrl = githubApiBranchUrl + branch;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(branchUrl))
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3+json")
            .build();
        log.info("Will send a GET request to {}", branchUrl);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Got the following response status code: {}", response.statusCode());
        log.info("Got the following response: {}", response.body());
        return response.statusCode() == 200;
    }

}
