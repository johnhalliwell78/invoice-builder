package com.invoicebuilder.user;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.user.dto.AcceptInviteRequest;
import com.invoicebuilder.user.dto.InviteInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous endpoints for invite recipients landing on the emailed link. */
@RestController
@RequestMapping("/api/v1/public/invites")
@Tag(name = "Public invites", description = "Anonymous invite lookup and acceptance")
public class PublicInviteController {

    private final UserService userService;

    public PublicInviteController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Look up an invite by its token")
    public ApiResponse<InviteInfoResponse> info(@PathVariable String token) {
        return ApiResponse.of(userService.inviteInfo(token));
    }

    @PostMapping("/{token}/accept")
    @Operation(summary = "Accept an invite: set name + password, activate the account")
    public ResponseEntity<Void> accept(@PathVariable String token,
                                       @Valid @RequestBody AcceptInviteRequest request) {
        userService.acceptInvite(token, request);
        return ResponseEntity.noContent().build();
    }
}
