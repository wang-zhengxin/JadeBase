package ai.jadebase.identity.api;

import ai.jadebase.identity.domain.AuthenticationException;
import ai.jadebase.identity.domain.JadeUser;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthenticatedRequest {

    public static final String USER_ATTRIBUTE = AuthenticatedRequest.class.getName() + ".user";

    private AuthenticatedRequest() { }

    public static JadeUser user(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ATTRIBUTE);
        if (value instanceof JadeUser user) return user;
        throw new AuthenticationException("请先登录");
    }
}
