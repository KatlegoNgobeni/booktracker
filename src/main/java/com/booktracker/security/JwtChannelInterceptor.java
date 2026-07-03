package com.booktracker.security;

import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * STOMP channel interceptor that authenticates WebSocket connections using JWT (NOTIF-02, T-09-11).
 *
 * <p><strong>Why a ChannelInterceptor (not HandshakeInterceptor / HTTP filter):</strong>
 * The existing {@link JwtAuthenticationFilter} (OncePerRequestFilter) does not run on WebSocket
 * upgrade requests after the HTTP→WS protocol switch. A {@link ChannelInterceptor} on the
 * {@code clientInboundChannel} sees every inbound STOMP frame and can set the session principal
 * at CONNECT time, which is then inherited by all subsequent frames in the same session.
 * Using a HandshakeInterceptor would require the token in a URL query param — exposing the JWT
 * in server access logs (RESEARCH Anti-Patterns, Pitfall 1).
 *
 * <p><strong>Authentication flow (RESEARCH Pattern 2):</strong>
 * <ol>
 *   <li>Intercepts the STOMP CONNECT frame</li>
 *   <li>Reads {@code Authorization: Bearer <jwt>} from STOMP native headers</li>
 *   <li>Validates via {@link JwtUtil#extractSubject} and {@link JwtUtil#isTokenExpired}</li>
 *   <li>Loads {@link UserDetails} via {@code UserDetailsService.loadUserByUsername(uuidString)}</li>
 *   <li>Sets {@code accessor.setUser(auth)} so the STOMP session principal is the authenticated user</li>
 *   <li>On any failure (missing header, JwtException): catches silently, leaves principal null,
 *       always returns the message (never null)</li>
 * </ol>
 *
 * <p><strong>Principal name == UUID string (RESEARCH Pitfall 2):</strong>
 * {@code accessor.setUser(auth)} where {@code auth.getName()} == {@code userDetails.getUsername()}
 * == {@code UserEntity.getId().toString()} (UUID string). This must exactly match the string
 * passed to {@code SimpMessagingTemplate.convertAndSendToUser(recipient.getUsername(), ...)}
 * in {@link com.booktracker.notification.NotificationService}. Any mismatch causes silent
 * delivery failure — no exception, no log, no notification.
 *
 * <p><strong>Non-CONNECT frames:</strong>
 * Only STOMP CONNECT is processed for authentication. SEND, SUBSCRIBE, DISCONNECT, and other
 * commands pass through unchanged — the principal is inherited from the session established
 * at CONNECT time.
 *
 * <p><strong>Security invariants:</strong>
 * <ul>
 *   <li>T-09-11: {@code accessor.setUser} cannot be overridden by subsequent client frames —
 *       only set at CONNECT time by this interceptor.</li>
 *   <li>T-09-12: If no principal is set (missing/invalid JWT), subscribing to
 *       {@code /user/queue/notifications} delivers nothing — Spring routes by principal name,
 *       and no principal means no routing, no data disclosure.</li>
 *   <li>T-09-14: Token is sent in STOMP CONNECT native header ({@code Authorization}),
 *       never in the WebSocket URL as a query param — avoids access-log leakage.</li>
 * </ul>
 *
 * <p><strong>No AbstractSecurityWebSocketMessageBrokerConfigurer:</strong>
 * That class is the legacy CSRF-focused WebSocket security integration — conflicts with the
 * {@code STATELESS} session policy in {@link com.booktracker.config.SecurityConfig}. This
 * {@code ChannelInterceptor} approach is the stateless JWT alternative.
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    /**
     * Constructor injection — mirrors the {@link JwtAuthenticationFilter} dependency pattern.
     *
     * @param jwtUtil            JWT utility for token validation and subject extraction
     * @param userDetailsService UUID-based user loader (UserEntity.getUsername() = UUID string)
     */
    public JwtChannelInterceptor(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Intercept inbound STOMP frames.
     *
     * <p>Only acts on {@link StompCommand#CONNECT} frames to set the session principal.
     * All other commands pass through unchanged.
     *
     * <p><strong>Contract (RESEARCH Pattern 2 / T-09-11):</strong>
     * <ul>
     *   <li>Valid Bearer JWT → {@code accessor.setUser(UsernamePasswordAuthenticationToken)}</li>
     *   <li>Missing or non-Bearer Authorization header → no-op, return message</li>
     *   <li>Invalid/expired/tampered JWT → catch {@link JwtException}, no-op, return message</li>
     *   <li>ALWAYS returns the message — never returns {@code null} (null drops the message)</li>
     * </ul>
     *
     * @param message the inbound STOMP frame
     * @param channel the client inbound message channel
     * @return the (possibly modified) message — never {@code null}
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Only authenticate on CONNECT — subsequent frames inherit the session principal
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String userId = jwtUtil.extractSubject(token);
                    // Double-check expiry (mirrors JwtAuthenticationFilter pattern)
                    if (userId != null && !jwtUtil.isTokenExpired(token)) {
                        UserDetails userDetails =
                                userDetailsService.loadUserByUsername(userId);
                        // Build authentication with the loaded UserDetails
                        // getName() == userDetails.getUsername() == UUID string (Pitfall 2)
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        // Set STOMP session principal — inherited by all frames in this session
                        accessor.setUser(auth);
                    }
                } catch (JwtException e) {
                    // T-09-11: Invalid/expired/malformed/unsigned token — do NOT throw.
                    // Connection proceeds without a principal; subscriptions to /user/**
                    // will silently deliver nothing (T-09-12).
                }
            }
        }

        // ALWAYS return message — returning null would silently drop the frame
        return message;
    }
}
