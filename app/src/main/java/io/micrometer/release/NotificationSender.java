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

import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
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
        return new BlueSkyNotifier();
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
            this.googleChatNotificationUrl = System.getenv("SPRING_RELEASE_GCHAT_WEBHOOK_URL");
        }

        @Override
        public void sendNotification(String repoName, String refName, MilestoneWithDeadline newMilestone) {
            log.info("Sending notification to ...");
            String version = refName.startsWith("v") ? refName.substring(1) : refName;
            String name = repoName.startsWith("micrometer") ? repoName : "micrometer-" + repoName;
            sendAnnouncingNotificationToReleaseChannel(name, version);
            sendPlanningNotificationToReleaseChannel(name, version, newMilestone);
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
            try {
                HttpResponse<String> send = HttpClient.newHttpClient().send(chatRequest, BodyHandlers.ofString());
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

        private final String uriRoot;

        private final String identifier;

        private final String password;

        BlueSkyNotifier(String uriRoot, String identifier, String password) {
            this.uriRoot = uriRoot;
            this.identifier = identifier;
            this.password = password;
        }

        BlueSkyNotifier() {
            this.uriRoot = "https://bsky.social";
            this.identifier = System.getenv("BLUESKY_HANDLE");
            this.password = System.getenv("BLUESKY_PASSWORD");
        }

        @Override
        public void sendNotification(String repoName, String refName, MilestoneWithDeadline newMilestoneId) {
            HttpRequest blueskyRequest = HttpRequest.newBuilder()
                .uri(URI.create(uriRoot + "/xrpc/com.atproto.server.createSession"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers
                    .ofString("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"))
                .build();

            try {
                HttpResponse<String> blueskyResponse = HttpClient.newHttpClient()
                    .send(blueskyRequest, HttpResponse.BodyHandlers.ofString());
                log.info("Bluesky response: " + blueskyResponse.body());
                if (blueskyResponse.statusCode() >= 400) {
                    throw new IllegalStateException("Unexpected response code: " + blueskyResponse.statusCode());
                }
            }
            catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

}
