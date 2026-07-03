package com.booktracker.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for {@link JwtChannelInterceptor} (NOTIF-02 / T-09-11).
 *
 * <p>No Spring context or Testcontainers — validates the JWT STOMP authentication logic in isolation.
 *
 * <p><strong>RED phase (Task 1):</strong> Both cases fail because {@link JwtChannelInterceptor}
 * does not exist yet. Task 2 creates it.
 *
 * <p>Test cases:
 * <ul>
 *   <li>{@link #validJwtOnConnectSetsPrincipalToUserUuid} — valid Bearer JWT → principal name = user UUID</li>
 *   <li>{@link #missingJwtOnConnectLeavesNoPrincipal} — no Authorization header → no principal set</li>
 *   <li>{@link #invalidJwtOnConnectLeavesNoPrincipal} — malformed/expired token → no principal set</li>
 *   <li>{@link #interceptorAlwaysReturnsMessage} — preSend NEVER returns null, even with bad token</li>
 * </ul>
 *
 * <p><strong>Principal name == UUID string (RESEARCH Pitfall 2):</strong>
 * The principal set by {@link JwtChannelInterceptor} must use the UUID string as the name
 * so {@code SimpMessagingTemplate.convertAndSendToUser()} routes correctly.
 * {@link UserEntity#getUsername()} returns {@code id.toString()}, so loading via
 * {@code userDetailsService.loadUserByUsername(uuidString)} produces a principal whose
 * {@code getName()} is the UUID string.
 */
class JwtChannelInterceptorTest {

    /** Test JWT secret — same 32-byte base64 value used in NotificationIntegrationTest. */
    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==";

    private JwtUtil jwtUtil;
    private UserDetailsService userDetailsService;
    private JwtChannelInterceptor interceptor;

    /** Known test user UUID (used as JWT sub + loaded via UserDetailsService mock). */
    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Configure JwtUtil with the test secret via the package-private setter
        jwtUtil = new JwtUtil();
        jwtUtil.setJwtSecret(TEST_SECRET);

        // Mock UserDetailsService — loadUserByUsername(uuid.toString()) returns a UserDetails
        // whose getUsername() == uuid.toString() (mirrors UserEntity.getUsername() in production)
        userDetailsService = mock(UserDetailsService.class);
        UserDetails userDetails = new User(
                testUserId.toString(),    // username = UUID string (RESEARCH Pitfall 2)
                "ignored",
                Collections.emptyList()
        );
        when(userDetailsService.loadUserByUsername(testUserId.toString())).thenReturn(userDetails);

        // Interceptor under test — created AFTER jwtUtil secret is injected
        interceptor = new JwtChannelInterceptor(jwtUtil, userDetailsService);
    }

    /**
     * NOTIF-02 / T-09-11: A STOMP CONNECT with a valid JWT Bearer token must set the
     * STOMP session principal to a {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken}
     * whose {@code getName()} equals the user's UUID string.
     */
    @Test
    void validJwtOnConnectSetsPrincipalToUserUuid() {
        // Generate a valid token for the test user
        String token = jwtUtil.generateToken(testUserId.toString());

        // Build a STOMP CONNECT message with Authorization: Bearer <token>
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders());

        // Invoke the interceptor
        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        // The result message must not be null (interceptor must always return message)
        assertThat(result).isNotNull();

        // Re-read the accessor from the returned message to check if principal was set
        StompHeaderAccessor resultAccessor =
                MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo(testUserId.toString());
    }

    /**
     * NOTIF-02 / T-09-12: A STOMP CONNECT with NO Authorization header must leave the
     * session principal unset (null). Subscribing to /user/queue/notifications will then
     * silently deliver nothing — no data leak, no exception.
     */
    @Test
    void missingJwtOnConnectLeavesNoPrincipal() {
        // CONNECT with no Authorization header at all
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(result).isNotNull();

        StompHeaderAccessor resultAccessor =
                MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNull();
    }

    /**
     * NOTIF-02 / T-09-11: A STOMP CONNECT with an INVALID JWT (tampered/expired) must
     * leave the session principal unset (null). The interceptor catches the JwtException
     * and returns the message unchanged — preSend never throws.
     */
    @Test
    void invalidJwtOnConnectLeavesNoPrincipal() {
        String malformedToken = "not.a.valid.jwt.token";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + malformedToken);
        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(result).isNotNull();

        StompHeaderAccessor resultAccessor =
                MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNull();
    }

    /**
     * NOTIF-02: Verify that preSend ALWAYS returns the message (never null), regardless of
     * Authorization header state. Returning null from preSend would drop the message.
     */
    @Test
    void interceptorAlwaysReturnsMessage() {
        // Test 1: No header
        StompHeaderAccessor accessor1 = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor1.setLeaveMutable(true);
        Message<byte[]> msg1 = MessageBuilder.createMessage(new byte[0], accessor1.getMessageHeaders());
        assertThat(interceptor.preSend(msg1, mock(MessageChannel.class))).isNotNull();

        // Test 2: Junk token
        StompHeaderAccessor accessor2 = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor2.addNativeHeader("Authorization", "Bearer garbage");
        accessor2.setLeaveMutable(true);
        Message<byte[]> msg2 = MessageBuilder.createMessage(new byte[0], accessor2.getMessageHeaders());
        assertThat(interceptor.preSend(msg2, mock(MessageChannel.class))).isNotNull();

        // Test 3: Non-CONNECT command — should pass through unchanged
        StompHeaderAccessor accessor3 = StompHeaderAccessor.create(StompCommand.SEND);
        accessor3.setLeaveMutable(true);
        Message<byte[]> msg3 = MessageBuilder.createMessage(new byte[0], accessor3.getMessageHeaders());
        assertThat(interceptor.preSend(msg3, mock(MessageChannel.class))).isNotNull();
    }
}
