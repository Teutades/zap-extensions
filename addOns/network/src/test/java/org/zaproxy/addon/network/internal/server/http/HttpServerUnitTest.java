/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2022 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.addon.network.internal.server.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.Attribute;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import java.io.IOException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.parosproxy.paros.network.HttpHeader;
import org.zaproxy.addon.network.internal.ChannelAttributes;
import org.zaproxy.addon.network.internal.TlsUtils;
import org.zaproxy.addon.network.internal.cert.ServerCertificateService;
import org.zaproxy.addon.network.internal.codec.HttpToHttp2ConnectionHandler;
import org.zaproxy.addon.network.server.Server;

/** Unit test for {@link HttpServer}. */
class HttpServerUnitTest {

    private static NioEventLoopGroup group;
    private static EventExecutorGroup mainHandlerExecutor;
    private ServerCertificateService certificateService;
    private Supplier<MainServerHandler> handlerSupplier;

    @BeforeAll
    static void setupAll() throws Exception {
        group = new NioEventLoopGroup(1, new DefaultThreadFactory("ZAP-HttpServerUnitTest"));
        mainHandlerExecutor =
                new DefaultEventExecutorGroup(
                        1, new DefaultThreadFactory("ZAP-HttpServerUnitTest-Events"));
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }

        if (mainHandlerExecutor != null) {
            mainHandlerExecutor.shutdownGracefully();
            mainHandlerExecutor = null;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        certificateService = mock(ServerCertificateService.class);
        handlerSupplier = () -> mock(MainServerHandler.class);
    }

    @Test
    void shouldThrowIfNoEventLoopGroup() throws Exception {
        // Given
        NioEventLoopGroup group = null;
        // When / Then
        assertThrows(
                NullPointerException.class,
                () ->
                        new HttpServer(
                                group, mainHandlerExecutor, certificateService, handlerSupplier));
    }

    @Test
    void shouldThrowIfNoEventExecutorGroup() throws Exception {
        // Given
        EventExecutorGroup mainHandlerExecutor = null;
        // When / Then
        assertThrows(
                NullPointerException.class,
                () ->
                        new HttpServer(
                                group, mainHandlerExecutor, certificateService, handlerSupplier));
    }

    @Test
    void shouldThrowIfNoServerCertificateService() throws Exception {
        // Given
        certificateService = null;
        // When / Then
        assertThrows(
                NullPointerException.class,
                () ->
                        new HttpServer(
                                group, mainHandlerExecutor, certificateService, handlerSupplier));
    }

    @Test
    void shouldThrowIfNoMainHandlerSupplier() throws Exception {
        // Given
        handlerSupplier = null;
        // When / Then
        assertThrows(
                NullPointerException.class,
                () ->
                        new HttpServer(
                                group, mainHandlerExecutor, certificateService, handlerSupplier));
    }

    @Test
    void shouldCreate() throws Exception {
        assertDoesNotThrow(
                () ->
                        new HttpServer(
                                group, mainHandlerExecutor, certificateService, handlerSupplier));
    }

    @Test
    void shouldCreateWithNoHandler() throws Exception {
        assertDoesNotThrow(() -> new HttpServer(group, mainHandlerExecutor, certificateService));
    }

    @Test
    void shouldFailToStartWithNoHandler() throws Exception {
        try (HttpServer server = new HttpServer(group, mainHandlerExecutor, certificateService)) {
            IOException exception =
                    assertThrows(IOException.class, () -> server.start(Server.ANY_PORT));
            assertThat(exception.getMessage(), is(equalTo("No main server handler set.")));
        }
    }

    @Test
    void shouldStartWithHandlerSet() throws Exception {
        try (HttpServer server = new HttpServer(group, mainHandlerExecutor, certificateService)) {
            server.setMainServerHandler(handlerSupplier);
            assertDoesNotThrow(() -> server.start(Server.ANY_PORT));
        }
    }

    @Test
    void shouldThrowIfSettingNullHandler() throws Exception {
        // Given
        handlerSupplier = null;
        // When / Then
        try (HttpServer server = new HttpServer(group, mainHandlerExecutor, certificateService)) {
            assertThrows(
                    NullPointerException.class, () -> server.setMainServerHandler(handlerSupplier));
        }
    }

    @Test
    void shouldNotChangePipelineForHttp1() throws Exception {
        // Given
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        String protocol = TlsUtils.APPLICATION_PROTOCOL_HTTP_1_1;
        // When
        HttpServer.protocolConfiguration(ctx, protocol);
        // Then
        verifyNoInteractions(ctx);
    }

    @ParameterizedTest
    @ValueSource(strings = {HttpHeader.HTTP, HttpHeader.HTTPS})
    void shouldReconfigurePipelineForHttp2(String scheme) throws Exception {
        // Given
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        given(ctx.pipeline()).willReturn(pipeline);
        Channel channel = mock(Channel.class);
        given(ctx.channel()).willReturn(channel);
        @SuppressWarnings("unchecked")
        Attribute<Boolean> attribute = mock(Attribute.class);
        given(channel.attr(ChannelAttributes.TLS_UPGRADED)).willReturn(attribute);
        given(attribute.get()).willReturn(HttpHeader.HTTPS.equals(scheme));
        InOrder inOrder = inOrder(pipeline);
        String protocol = TlsUtils.APPLICATION_PROTOCOL_HTTP_2;
        // When
        HttpServer.protocolConfiguration(ctx, protocol);
        // Then
        inOrder.verify(pipeline).remove("timeout");
        inOrder.verify(pipeline).remove("http.decoder");
        inOrder.verify(pipeline).remove("logging");
        ArgumentCaptor<HttpToHttp2ConnectionHandler> connectionHandlerCaptor =
                ArgumentCaptor.forClass(HttpToHttp2ConnectionHandler.class);
        inOrder.verify(pipeline)
                .replace(eq("http.encoder"), eq("http2.codec"), connectionHandlerCaptor.capture());
        HttpToHttp2ConnectionHandler connectionHandler = connectionHandlerCaptor.getValue();
        assertThat(connectionHandler.getDefaultScheme(), is(equalTo(scheme)));
        verifyNoMoreInteractions(pipeline);
        verify(ctx).pipeline();
        verify(ctx).channel();
        verifyNoMoreInteractions(ctx);
    }

    @Test
    void shouldCloseConnectionForUnsupportedApplicationProtocol() throws Exception {
        // Given
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        String protocol = "Unsupported Protocol";
        // When
        HttpServer.protocolConfiguration(ctx, protocol);
        // Then
        verify(ctx).close();
        verifyNoMoreInteractions(ctx);
    }
}
