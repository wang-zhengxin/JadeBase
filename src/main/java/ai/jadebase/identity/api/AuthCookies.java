package ai.jadebase.identity.api;

import ai.jadebase.identity.domain.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Arrays;

final class AuthCookies {

    private AuthCookies() { }

    static String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> AuthService.SESSION_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    static void write(HttpServletResponse response, String token, boolean secure) {
        add(response, token, AuthService.SESSION_TTL, secure);
    }

    static void clear(HttpServletResponse response, boolean secure) {
        add(response, "", Duration.ZERO, secure);
    }

    private static void add(HttpServletResponse response, String token, Duration maxAge, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(AuthService.SESSION_COOKIE, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
