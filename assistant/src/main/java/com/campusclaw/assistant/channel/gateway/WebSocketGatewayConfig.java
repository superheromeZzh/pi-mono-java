package com.campusclaw.assistant.channel.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

/**
 * WebSocket Gateway server configuration using Netty.
 * Uses SmartLifecycle to start after all beans are initialized.
 */
@Configuration
@ConditionalOnProperty(prefix = "pi.assistant.gateway", name = "enabled", havingValue = "true")
public class WebSocketGatewayConfig implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WebSocketGatewayConfig.class);

    private final WebSocketGatewayProperties properties;
    private final GatewayChannel gatewayChannel;
    private final ObjectMapper objectMapper;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running = false;

    public WebSocketGatewayConfig(WebSocketGatewayProperties properties, GatewayChannel gatewayChannel,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.gatewayChannel = gatewayChannel;
        this.objectMapper = objectMapper;
    }

    @Bean
    public GatewayWebSocketHandler gatewayWebSocketHandler() {
        return new GatewayWebSocketHandler(properties, gatewayChannel, objectMapper);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            GatewayWebSocketHandler handler = gatewayWebSocketHandler();

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        // HTTP codec
                        pipeline.addLast("httpCodec", new HttpServerCodec());

                        // Aggregate HTTP messages
                        pipeline.addLast("httpAggregator", new HttpObjectAggregator(65536));

                        // WebSocket protocol handler
                        pipeline.addLast("websocketHandler", new WebSocketServerProtocolHandler(
                            properties.getPath(), null, true, 65536, false, true
                        ));

                        // Custom message handler
                        pipeline.addLast("messageHandler", handler);
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

            // Bind and start to accept incoming connections
            serverChannel = bootstrap.bind(new InetSocketAddress(properties.getPort())).sync().channel();
            running = true;

        } catch (Exception e) {
            log.error("Failed to start WebSocket Gateway: {}", e.getMessage(), e);
            stop();
        }
    }

    @Override
    public void stop() {
        running = false;

        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Start late, after other beans are ready
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}