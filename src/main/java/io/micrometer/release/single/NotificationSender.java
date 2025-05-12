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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.release.common.Input;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final List<Notifier> notifiers;

    NotificationSender() {
        this.notifiers = List.of(googleChat(), blueSky());
    }

    void sendNotifications(String repoName, String refName, MilestoneWithDeadline newMilestoneId) {
        notifiers.forEach(notifier -> notifier.sendNotification(repoName, refName, newMilestoneId));
    }

    // for tests
    BlueSkyNotifier blueSky() {
        return new BlueSkyNotifier(new ObjectMapper());
    }

    // for tests
    GoogleChatNotifier googleChat() {
        return new GoogleChatNotifier();
    }

    interface Notifier {

        void sendNotification(String repoName, String refName, MilestoneWithDeadline newMilestoneId);

    }

    static class GoogleChatNotifier implements Notifier {

        private final String googleChatNotificationUrl;

        GoogleChatNotifier(String googleChatNotificationUrl) {
            this.googleChatNotificationUrl = googleChatNotificationUrl;
        }

        GoogleChatNotifier() {
            this.googleChatNotificationUrl = Input.getGChatWebhookUrl();
        }

        @Override
        public void sendNotification(String repoName, String refName, MilestoneWithDeadline newMilestone) {
            if (googleChatNotificationUrl == null || googleChatNotificationUrl.isBlank()) {
                log.warn("Won't send notification to GChat - webhook url is missing");
                return;
            }
            log.info("Sending notification to GChat");
            String version = refName.startsWith("v") ? refName.substring(1) : refName;
            String name = repoName.startsWith("micrometer") ? repoName : "micrometer-" + repoName;
            sendAnnouncingNotificationToReleaseChannel(name, version);
            sendPlanningNotificationToReleaseChannel(name, ReleaseVersionCalculator.calculateNextVersion(version),
                    newMilestone);
        }

        private void sendAnnouncingNotificationToReleaseChannel(String name, String version) {
            String payload = String.format("{\"text\": \"%s-announcing %s\"}", name, version);
            notifyGoogleChat(payload);
        }

        private void sendPlanningNotificationToReleaseChannel(String name, String version,
                MilestoneWithDeadline milestone) {
            if (milestone == null) {
                return;
            }
            String formattedDate = milestone.deadline().format(DateTimeFormatter.ofPattern("MMMM d"));
            String payload = String.format("{\"text\": \"%s-planning %s on %s\"}", name, version, formattedDate);
            notifyGoogleChat(payload);
        }

        private void notifyGoogleChat(String payload) {
            // Google Chat Notification
            HttpRequest chatRequest = HttpRequest.newBuilder()
                .uri(URI.create(googleChatNotificationUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            try (HttpClient httpClient = HttpClient.newHttpClient()) {
                HttpResponse<String> send = httpClient.send(chatRequest, BodyHandlers.ofString());
                if (send.statusCode() >= 400) {
                    throw new IllegalStateException("Unexpected response code: " + send.statusCode());
                }
            }
            catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    static class BlueSkyNotifier implements Notifier {

        private static final Logger log = LoggerFactory.getLogger(BlueSkyNotifier.class);

        private final ObjectMapper objectMapper;

        private final String uriRoot;

        private final String identifier;

        private final String password;

        BlueSkyNotifier(ObjectMapper objectMapper, String uriRoot, String identifier, String password) {
            this.objectMapper = objectMapper;
            this.uriRoot = uriRoot;
            this.identifier = identifier;
            this.password = password;
        }

        BlueSkyNotifier(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            this.uriRoot = "https://bsky.social";
            this.identifier = Input.getBlueSkyHandle();
            this.password = Input.getBlueSkyPassword();
        }

        @Override
        public void sendNotification(String repoName, String refName, MilestoneWithDeadline newMilestoneId) {
            if (password == null || password.isBlank()) {
                log.warn("Won't send notification to Bluesky - no password provided");
                return;
            }

            String token = getToken();

            createPost(token, createPostJson(repoName, refName));
        }

        private String getToken() {
            HttpRequest createSessionRequest = HttpRequest.newBuilder()
                .uri(URI.create(uriRoot + "/xrpc/com.atproto.server.createSession"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                    .ofString("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"))
                .build();

            try (HttpClient httpClient = HttpClient.newHttpClient()) {
                HttpResponse<String> createSessionResponse = httpClient.send(createSessionRequest,
                        HttpResponse.BodyHandlers.ofString());
                if (createSessionResponse.statusCode() >= 400) {
                    throw new IllegalStateException("Unexpected response code: " + createSessionResponse.statusCode());
                }
                JsonNode jsonNode = objectMapper.readTree(createSessionResponse.body());
                if (!jsonNode.has("accessJwt")) {
                    throw new IllegalStateException("Missing JWT in response");
                }
                return jsonNode.get("accessJwt").asText();
            }
            catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void createPost(String token, String postJson) {
            String requestBody = """
                    {
                      "repo":"%s",
                      "collection":"app.bsky.feed.post",
                      "record":%s
                    }""".formatted(identifier, postJson);
            HttpRequest createRecordRequest = HttpRequest.newBuilder()
                .uri(URI.create(uriRoot + "/xrpc/com.atproto.repo.createRecord"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            try (HttpClient httpClient = HttpClient.newHttpClient()) {
                HttpResponse<String> createRecordResponse = httpClient.send(createRecordRequest,
                        BodyHandlers.ofString());
                if (createRecordResponse.statusCode() >= 400) {
                    log.error("Unexpected response code: {}\nResponse: {}\nRequest: {}",
                            createRecordResponse.statusCode(), createRecordResponse.body(), requestBody);
                    throw new IllegalStateException("Unexpected response code: " + createRecordResponse.statusCode());
                }
                else {
                    log.debug("Created record: Request: {}, Response: {}", requestBody, createRecordResponse.body());
                }
                String postRevision = objectMapper.readTree(createRecordResponse.body()).at("/commit/rev").asText();
                log.info("Bluesky post created: https://bsky.app/profile/{}/post/{}", identifier, postRevision);
            }
            catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private String createPostJson(String projectName, String versionRef) {
            String version = versionRef.startsWith("v") ? versionRef.substring(1) : versionRef;
            String postText = """
                    %s %s has been released!

                    Check out the changelog at https://github.com/micrometer-metrics/%s/releases/tag/%s\
                    """.formatted(projectName, version, projectName, versionRef);
            String facetsJson = createFacetsJson(postText);
            return """
                    {
                      "$type": "app.bsky.feed.post",
                      "text": "%s",
                      "createdAt": "%s",
                      "facets": [
                        %s
                      ]
                    }""".formatted(postText, ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT), facetsJson);
        }

        private String createFacetsJson(String postText) {
            // TODO this is needed for the URL in the post to be a hyperlink
            return "";
        }

    }

}
