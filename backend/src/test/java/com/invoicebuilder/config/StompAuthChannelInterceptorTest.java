package com.invoicebuilder.config;

import com.invoicebuilder.auth.jwt.JwtService;
import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock private JwtService jwtService;

    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> connectMessage(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void bindsUserPrincipalFromValidBearerToken() {
        UUID userId = UUID.randomUUID();
        when(jwtService.parse("good-token")).thenReturn(
                new JwtService.ParsedToken(userId, UUID.randomUUID(), Role.OWNER, "u@x.io"));
        StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtService);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer good-token");
        interceptor.preSend(connectMessage(accessor), channel);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(userId.toString());
    }

    @Test
    void leavesSessionUnauthenticatedForInvalidToken() {
        when(jwtService.parse("bad")).thenThrow(new AppException(ErrorCode.INVALID_TOKEN, "nope"));
        StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtService);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer bad");
        interceptor.preSend(connectMessage(accessor), channel);

        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void leavesSessionUnauthenticatedWhenNoAuthHeader() {
        StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtService);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        interceptor.preSend(connectMessage(accessor), channel);

        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void ignoresNonConnectFrames() {
        StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtService);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.addNativeHeader("Authorization", "Bearer good-token");
        interceptor.preSend(connectMessage(accessor), channel);

        assertThat(accessor.getUser()).isNull();
    }
}
