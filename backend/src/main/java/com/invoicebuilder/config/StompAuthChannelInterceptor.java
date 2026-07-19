package com.invoicebuilder.config;

import com.invoicebuilder.auth.jwt.JwtService;
import com.invoicebuilder.common.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP {@code CONNECT} frame from the {@code Authorization:
 * Bearer ...} header (the SPA sends its in-memory access token here). On
 * success the user id is bound as the session principal so per-user
 * destinations route correctly. An invalid or missing token leaves the session
 * unauthenticated — it simply never receives user-scoped messages.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public StompAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                try {
                    JwtService.ParsedToken parsed = jwtService.parse(header.substring(BEARER_PREFIX.length()));
                    accessor.setUser(new StompPrincipal(parsed.userId().toString()));
                } catch (AppException e) {
                    log.debug("STOMP CONNECT with invalid token: {}", e.getMessage());
                }
            }
        }
        return message;
    }
}
