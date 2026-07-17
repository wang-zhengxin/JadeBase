package ai.jadebase.identity.admin;

import ai.jadebase.identity.api.AuthenticatedRequest;
import ai.jadebase.identity.domain.JadeUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public UserAdminService.UserPageView list(HttpServletRequest request,
                                               @RequestParam(defaultValue = "") String query,
                                               @RequestParam(defaultValue = "all") String role,
                                               @RequestParam(defaultValue = "all") String status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        requireOwner(request);
        return service.list(query, role, status, page, size);
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdminService.CreatedInvitation invite(HttpServletRequest request,
                                                      @RequestBody UserAdminService.InviteInput input) {
        JadeUser actor = requireOwner(request);
        return service.invite(actor, input);
    }

    @DeleteMapping("/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(HttpServletRequest request, @PathVariable UUID invitationId) {
        requireOwner(request);
        service.revokeInvitation(invitationId);
    }

    @PatchMapping("/{userId}")
    public UserAdminService.UserView update(HttpServletRequest request, @PathVariable UUID userId,
                                             @RequestBody UserAdminService.UpdateUserInput input) {
        return service.updateUser(requireOwner(request), userId, input);
    }

    @PutMapping("/registration-policy")
    public UserAdminService.AccessPolicyView updatePolicy(HttpServletRequest request,
                                                           @RequestBody RegistrationPolicyInput input) {
        requireOwner(request);
        return service.updateAccessPolicy(input.restrictOpenSignup());
    }

    private JadeUser requireOwner(HttpServletRequest request) {
        JadeUser user = AuthenticatedRequest.user(request);
        if (user.getRole() != JadeUser.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有工作区所有者可以管理用户");
        }
        return user;
    }

    public record RegistrationPolicyInput(boolean restrictOpenSignup) { }
}
