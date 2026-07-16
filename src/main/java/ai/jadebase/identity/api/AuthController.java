package ai.jadebase.identity.api;

import ai.jadebase.identity.domain.AuthService;
import ai.jadebase.identity.domain.JadeUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(AuthService authService,
                          @Value("${jadebase.auth.secure-cookie:false}") boolean secureCookie) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody Credentials request,
                                                  HttpServletResponse response) {
        AuthService.SessionResult session = authService.register(request.email(), request.password());
        AuthCookies.write(response, session.token(), secureCookie);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(session.user()));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody Credentials request, HttpServletResponse response) {
        AuthService.SessionResult session = authService.login(request.email(), request.password());
        AuthCookies.write(response, session.token(), secureCookie);
        return UserResponse.from(session.user());
    }

    @GetMapping("/me")
    public UserResponse me(HttpServletRequest request) {
        return UserResponse.from(AuthenticatedRequest.user(request));
    }

    @PatchMapping("/me")
    public UserResponse update(@Valid @RequestBody UpdateProfile request, HttpServletRequest servletRequest) {
        JadeUser user = AuthenticatedRequest.user(servletRequest);
        return UserResponse.from(authService.updateDisplayName(user.getId(), request.displayName()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePassword request,
                                               HttpServletRequest servletRequest) {
        JadeUser user = AuthenticatedRequest.user(servletRequest);
        authService.changePassword(user.getId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(AuthCookies.read(request));
        AuthCookies.clear(response, secureCookie);
        return ResponseEntity.noContent().build();
    }

    public record Credentials(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password) { }

    public record UpdateProfile(@Size(max = 40) String displayName) { }

    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) { }

    public record UserResponse(UUID id, String email, String displayName, String role,
                               Instant createdAt, Instant updatedAt) {
        static UserResponse from(JadeUser user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getRole().name().toLowerCase(Locale.ROOT), user.getCreatedAt(), user.getUpdatedAt());
        }
    }
}
