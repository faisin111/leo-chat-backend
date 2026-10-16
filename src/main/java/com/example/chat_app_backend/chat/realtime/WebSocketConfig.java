package com.example.chat_app_backend.chat.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final String[] allowedOrigins;
  private final WsAuthInterceptor wsAuthInterceptor;

  public WebSocketConfig(
      WsAuthInterceptor wsAuthInterceptor,
      @Value("${app.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins) {
    this.wsAuthInterceptor = wsAuthInterceptor;
    this.allowedOrigins = allowedOrigins;
  }

  @Bean
  public ThreadPoolTaskScheduler heartbeatScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("ws-heartbeat-");
    return scheduler;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {

    config
        .enableSimpleBroker("/topic", "/queue")
        .setTaskScheduler(heartbeatScheduler())
        .setHeartbeatValue(new long[] {10000, 10000});
    config.setApplicationDestinationPrefixes("/app");
    config.setUserDestinationPrefix("/user");
    config.setPreservePublishOrder(true);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins).withSockJS();
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
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
