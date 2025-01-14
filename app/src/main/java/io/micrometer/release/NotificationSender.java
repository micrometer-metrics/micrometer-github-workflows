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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class NotificationSender {

    private static final String SPRING_RELEASE_WEBHOOK = System.getenv("SPRING_RELEASE_GCHAT_WEBHOOK_URL");

    private static final String BLUESKY_IDENTIFIER = System.getenv("BLUESKY_HANDLE");

    private static final String BLUESKY_PASSWORD = System.getenv("BLUESKY_PASSWORD");

    void sendNotifications() throws IOException, InterruptedException {
        System.out.println("Sending notifications...");

        // Google Chat Notification
        HttpRequest chatRequest = HttpRequest.newBuilder()
            .uri(URI.create(SPRING_RELEASE_WEBHOOK))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"Release has been announced!\"}"))
            .build();
        HttpClient.newHttpClient().send(chatRequest, HttpResponse.BodyHandlers.ofString());

        // Bluesky Notification
        HttpRequest blueskyRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://bsky.social/xrpc/com.atproto.server.createSession"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers
                .ofString("{\"identifier\":\"" + BLUESKY_IDENTIFIER + "\",\"password\":\"" + BLUESKY_PASSWORD + "\"}"))
            .build();
        HttpResponse<String> blueskyResponse = HttpClient.newHttpClient()
            .send(blueskyRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("Bluesky response: " + blueskyResponse.body());
    }

}
