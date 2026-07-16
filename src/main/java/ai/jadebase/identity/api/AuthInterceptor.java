package ai.jadebase.identity.api;

import ai.jadebase.identity.domain.AuthService;
import ai.jadebase.identity.domain.JadeUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        JadeUser user = authService.authenticate(AuthCookies.read(request));
        request.setAttribute(AuthenticatedRequest.USER_ATTRIBUTE, user);
        return true;
    }
}
