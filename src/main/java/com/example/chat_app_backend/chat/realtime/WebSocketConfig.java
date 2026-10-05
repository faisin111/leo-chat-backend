package com.example.chat_app_backend.chat.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WsAuthInterceptor wsAuthInterceptor;

    public WebSocketConfig(WsAuthInterceptor wsAuthInterceptor) {
        this.wsAuthInterceptor = wsAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
                config.enableSimpleBroker("/topic", "/queue", "/user")
              .setTaskScheduler(new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler())
              .setHeartbeatValue(new long[]{10000, 10000});
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:3000", "https://your-frontend-domain.com")
                .withSockJS();
    }

        @Override
    public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(64 * 1024); // 64 KB
        registry.setSendBufferSizeLimit(512 * 1024); // 512 KB
        registry.setSendTimeLimit(20000); // 20s
    }

        @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(10).maxPoolSize(50);
        registration.interceptors(wsAuthInterceptor);
    }
}
