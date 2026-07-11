package com.invoicebuilder.user;

import com.invoicebuilder.common.dto.ApiResponse;
import com.invoicebuilder.user.dto.ChangeRoleRequest;
import com.invoicebuilder.user.dto.InviteRequest;
import com.invoicebuilder.user.dto.MemberResponse;
import com.invoicebuilder.user.dto.SetActiveRequest;
import com.invoicebuilder.user.dto.TransferOwnershipRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
@Tag(name = "Team", description = "Tenant member management (owner/admin only)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List tenant members")
    public ApiResponse<List<MemberResponse>> list() {
        return ApiResponse.of(userService.list());
    }

    @PostMapping("/invite")
    @Operation(summary = "Invite a new member by email (ADMIN or MEMBER)")
    public ResponseEntity<ApiResponse<MemberResponse>> invite(@Valid @RequestBody InviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(userService.invite(request)));
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Change a member's role (not the owner's)")
    public ApiResponse<MemberResponse> changeRole(@PathVariable UUID id,
                                                  @Valid @RequestBody ChangeRoleRequest request) {
        return ApiResponse.of(userService.changeRole(id, request));
    }

    @PutMapping("/{id}/active")
    @Operation(summary = "Activate or deactivate a member (not yourself, not the owner)")
    public ApiResponse<MemberResponse> setActive(@PathVariable UUID id,
                                                 @Valid @RequestBody SetActiveRequest request) {
        return ApiResponse.of(userService.setActive(id, request));
    }

    @PostMapping("/transfer-ownership")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Transfer workspace ownership to another active member")
    public ApiResponse<List<MemberResponse>> transferOwnership(
            @Valid @RequestBody TransferOwnershipRequest request) {
        return ApiResponse.of(userService.transferOwnership(request));
    }
}
