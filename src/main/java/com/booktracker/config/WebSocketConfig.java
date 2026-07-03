package com.booktracker.config;

import com.booktracker.security.JwtChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket / STOMP / SockJS configuration (NOTIF-02).
 *
 * <p><strong>Broker topology (RESEARCH Pattern 1):</strong>
 * <ul>
 *   <li>SimpleBroker destinations: {@code /topic} (broadcast), {@code /queue} (user-targeted)</li>
 *   <li>Application destination prefix: {@code /app} (routes to {@code @MessageMapping} methods)</li>
 *   <li>User destination prefix: {@code /user} — required for
 *       {@code SimpMessagingTemplate.convertAndSendToUser()} routing</li>
 * </ul>
 *
 * <p><strong>SockJS endpoint (RESEARCH Pattern 1):</strong>
 * Registered at {@code /ws} with {@code .withSockJS()} so the browser can use HTTP long-polling
 * as a fallback when native WebSocket is blocked by a corporate proxy.
 * {@code setAllowedOriginPatterns("*")} — acceptable for local dev; tighten in production
 * to the specific frontend origin.
 *
 * <p><strong>JWT authentication (RESEARCH Pattern 2):</strong>
 * The {@link JwtChannelInterceptor} is registered on the clientInboundChannel.
 * It authenticates STOMP CONNECT frames using the same JWT as the HTTP {@code JwtAuthenticationFilter}.
 * Subsequent frames in the same STOMP session inherit the principal set at CONNECT.
 *
 * <p><strong>SecurityConfig coordination (RESEARCH Pitfall 1):</strong>
 * The SockJS HTTP handshake and transport requests (all under {@code /ws/**}) must be
 * permitted in {@link SecurityConfig} BEFORE the {@code /api/**} authenticated rule.
 * Auth for WebSocket happens at the STOMP layer here, not the HTTP layer.
 *
 * <p><strong>No AbstractSecurityWebSocketMessageBrokerConfigurer (RESEARCH State of the Art):</strong>
 * That class is legacy CSRF-focused Spring Security WebSocket integration — conflicts with
 * the {@code STATELESS} session policy in this project. Use {@code ChannelInterceptor} instead.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    /**
     * Constructor injection — keeps dependency explicit and simplifies testing.
     *
     * @param jwtChannelInterceptor the STOMP CONNECT JWT authenticator
     */
    public WebSocketConfig(JwtChannelInterceptor jwtChannelInterceptor) {
        this.jwtChannelInterceptor = jwtChannelInterceptor;
    }

    /**
     * Configure the in-memory message broker.
     *
     * <p>Destinations:
     * <ul>
     *   <li>{@code /topic/**} — for broadcast topics (e.g. system-wide alerts, future use)</li>
     *   <li>{@code /queue/**} — for per-user targeted queues (notification delivery)</li>
     *   <li>{@code /app} — client-to-server prefix for {@code @MessageMapping} handlers</li>
     *   <li>{@code /user} — Spring's user-destination prefix, consumed by
     *       {@code convertAndSendToUser(uuid, "/queue/notifications", dto)}</li>
     * </ul>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory broker for /topic (broadcast) and /queue (user-targeted)
        config.enableSimpleBroker("/topic", "/queue");
        // Messages from client to server go to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
        // Required for convertAndSendToUser() routing — routes /user/{uuid}/queue/...
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Register the STOMP-over-SockJS endpoint at {@code /ws}.
     *
     * <p>The browser connects via:
     * {@code new SockJS('/ws')} + STOMP CONNECT with {@code Authorization: Bearer <jwt>} header.
     * SockJS provides HTTP long-polling fallback for proxied environments.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // CORS: allow all origins in dev; tighten to specific frontend URL in production
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Register the {@link JwtChannelInterceptor} on the client inbound channel.
     *
     * <p>All inbound messages from connected clients go through this interceptor.
     * The interceptor only acts on STOMP CONNECT frames — all other commands pass through
     * without modification.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
