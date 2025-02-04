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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NotificationSenderTests {

    @RegisterExtension
    static WireMockExtension wm1 = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Test
    void should_send_a_message_to_notifiers() {
        NotificationSender sender = testNotificationSender(wm1);

        sender.sendNotifications("micrometer", "v1.14.0", null);

        assertThatNotificationGotSent(wm1);
    }

    static void assertThatNotificationGotSent(WireMockExtension wireMockExtension) {
        wireMockExtension
            .verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/xrpc/com.atproto.server.createSession")));
        wireMockExtension.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/")));
    }

    static NotificationSender testNotificationSender(WireMockExtension extension) {
        return new NotificationSender() {
            @Override
            BlueSkyNotifier blueSky() {
                return new BlueSkyNotifier(extension.baseUrl(), "identifier", "password");
            }

            @Override
            GoogleChatNotifier googleChat() {
                return new GoogleChatNotifier(extension.baseUrl());
            }
        };
    }

}
