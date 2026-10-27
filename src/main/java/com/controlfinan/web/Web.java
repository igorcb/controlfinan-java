package com.controlfinan.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import com.controlfinan.model.Format;
import com.controlfinan.model.Repository;
import com.controlfinan.model.User;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Rails-like web helpers: signed cookies, session, CSRF token, flash, rate limit and passwords. */
@Component
public class Web {
    public static final String SESSION_COOKIE = "session_id";
    public static final String CSRF_COOKIE = "_csrf_token";
    public static final String FLASH_COOKIE = "_flash";
    public static final String RETURN_TO_COOKIE = "return_to";
    public static final String USER_ATTRIBUTE = "currentUser";
    public static final String SESSION_ATTRIBUTE = "currentSessionId";
    private static final int PERMANENT = 20 * 365 * 24 * 60 * 60; // cookies.permanent
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Repository repository;
    private final byte[] secret;
    private final int bcryptCost;
    private final ZoneId zone;
    private final Map<String, long[]> rateLimits = new ConcurrentHashMap<>();
    private final String dummyDigest;
    private Clock clock = Clock.systemUTC();

    public Web(Repository repository, @Value("${controlfinan.secret}") String secret, @Value("${controlfinan.bcrypt-cost}") int bcryptCost,
               @Value("${controlfinan.time-zone}") String timeZone) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("SECRET_KEY_BASE is required");
        }
        this.repository = repository;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.bcryptCost = bcryptCost;
        this.zone = ZoneId.of(timeZone);
        this.dummyDigest = hashPassword("dummy-password");
    }

    // Time (replaceable in tests)

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    // Passwords: bcrypt in the "$2a$" format Rails writes, using only the first 72 bytes like Rails.

    public String hashPassword(String raw) {
        return BCrypt.hashpw(truncate72(raw), BCrypt.gensalt("$2a", bcryptCost));
    }

    public boolean checkPassword(User user, String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(truncate72(raw), user.getPasswordDigest());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Burns the same time as a real check, so a missing e-mail cannot be told apart by timing. */
    public void checkDummyPassword() {
        BCrypt.checkpw("x", dummyDigest);
    }

    private static String truncate72(String raw) {
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        return bytes.length <= 72 ? raw : new String(bytes, 0, 72, StandardCharsets.UTF_8);
    }

    // Signed values ("value--signature")

    public String sign(String purpose, String value) {
        return B64.encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "--" + B64.encodeToString(hmac(purpose + ":" + value));
    }

    public String verify(String purpose, String signed) {
        if (signed == null) {
            return null;
        }
        int separator = signed.indexOf("--");
        if (separator < 0) {
            return null;
        }
        try {
            String value = new String(B64D.decode(signed.substring(0, separator)), StandardCharsets.UTF_8);
            return MessageDigest.isEqual(sign(purpose, value).getBytes(StandardCharsets.UTF_8), signed.getBytes(StandardCharsets.UTF_8)) ? value : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Cookies

    public static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static void setCookie(HttpServletResponse response, String name, String value, int maxAge) {
        StringBuilder header = new StringBuilder(name).append('=').append(value).append("; Path=/");
        if (maxAge != 0) {
            header.append("; Max-Age=").append(maxAge);
        }
        response.addHeader("Set-Cookie", header.append("; HttpOnly; SameSite=Lax").toString());
    }

    public static void deleteCookie(HttpServletResponse response, String name) {
        setCookie(response, name, "", -1);
    }

    public String readSigned(HttpServletRequest request, String name) {
        return verify(name, cookie(request, name));
    }

    public void setSigned(HttpServletResponse response, String name, String value, int maxAge) {
        setCookie(response, name, sign(name, value), maxAge);
    }

    // Authentication like Rails 8's generator: a signed "session_id" cookie pointing to a row in `sessions`.

    public static User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute(USER_ATTRIBUTE);
    }

    public void resumeSession(HttpServletRequest request) {
        String value = readSigned(request, SESSION_COOKIE);
        if (value == null) {
            return;
        }
        try {
            long sessionId = Long.parseLong(value);
            User user = repository.findUserBySession(sessionId);
            if (user != null) {
                request.setAttribute(USER_ATTRIBUTE, user);
                request.setAttribute(SESSION_ATTRIBUTE, sessionId);
            }
        } catch (NumberFormatException ignored) {
            // invalid cookie: stay signed out
        }
    }

    public void startNewSessionFor(HttpServletRequest request, HttpServletResponse response, User user) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > 255) {
            userAgent = userAgent.substring(0, 255);
        }
        long sessionId = repository.createSession(user.getId(), userAgent, request.getRemoteAddr());
        setSigned(response, SESSION_COOKIE, String.valueOf(sessionId), PERMANENT);
    }

    public void terminateSession(HttpServletRequest request, HttpServletResponse response) {
        Object sessionId = request.getAttribute(SESSION_ATTRIBUTE);
        if (sessionId != null) {
            repository.deleteSession((Long) sessionId);
        }
        deleteCookie(response, SESSION_COOKIE);
    }

    public String afterAuthenticationUrl(HttpServletRequest request, HttpServletResponse response) {
        String url = readSigned(request, RETURN_TO_COOKIE);
        if (url != null) {
            deleteCookie(response, RETURN_TO_COOKIE);
            return url;
        }
        return absoluteUrl(request, "/");
    }

    public static String absoluteUrl(HttpServletRequest request, String path) {
        return "http://" + request.getHeader("Host") + path;
    }

    // CSRF: a random token kept in a cookie and repeated in every form (double submit).

    public String csrfToken(HttpServletRequest request, HttpServletResponse response) {
        String token = cookie(request, CSRF_COOKIE);
        if (token == null || token.isEmpty()) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = B64.encodeToString(bytes);
            setCookie(response, CSRF_COOKIE, token, 0);
        }
        return token;
    }

    public static boolean validCsrf(HttpServletRequest request) {
        String cookie = cookie(request, CSRF_COOKIE);
        String param = request.getParameter("authenticity_token");
        return cookie != null && !cookie.isEmpty() && param != null
            && MessageDigest.isEqual(cookie.getBytes(StandardCharsets.UTF_8), param.getBytes(StandardCharsets.UTF_8));
    }

    // Flash messages survive one redirect, like Rails' flash ("notice" or "alert").

    public String redirect(HttpServletResponse response, String url, String kind, String message) {
        setSigned(response, FLASH_COOKIE, kind + ":" + message, 0);
        return "redirect:" + url;
    }

    /** Adds what every page needs (current user, flash messages and CSRF token) and returns the template. */
    public String render(String template, Model model, HttpServletRequest request, HttpServletResponse response) {
        String flash = readSigned(request, FLASH_COOKIE);
        if (flash != null) {
            deleteCookie(response, FLASH_COOKIE);
            String[] parts = flash.split(":", 2);
            model.addAttribute(parts[0], parts[1]);
        }
        model.addAttribute("user", currentUser(request));
        model.addAttribute("csrf", csrfToken(request, response));
        model.addAttribute("F", Format.INSTANCE);
        return template;
    }

    // Rate limit like Rails `rate_limit to: 10, within: 3.minutes`: a counter per action and IP.

    /** Forgets every counter (used by the tests). */
    public void clearRateLimits() {
        rateLimits.clear();
    }

    public boolean allow(String key, int to, Duration within) {
        long now = clock.millis();
        long[] entry = rateLimits.compute(key, (k, current) -> {
            if (current == null || now > current[1]) {
                return new long[] {1, now + within.toMillis()};
            }
            current[0]++;
            return current;
        });
        return entry[0] <= to;
    }

    // Password reset token: signed, expires in 15 minutes and stops working once the password changes.

    public String passwordResetToken(User user) {
        String digest = user.getPasswordDigest();
        String payload = user.getId() + ":" + digest.substring(digest.length() - 10) + ":" + now().plus(Duration.ofMinutes(15)).getEpochSecond();
        return B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + B64.encodeToString(hmac("password_reset:" + payload));
    }

    public User findByPasswordResetToken(String token) {
        int separator = token.indexOf('.');
        if (separator < 0) {
            return null;
        }
        try {
            String payload = new String(B64D.decode(token.substring(0, separator)), StandardCharsets.UTF_8);
            byte[] expected = B64.encodeToString(hmac("password_reset:" + payload)).getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, token.substring(separator + 1).getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            String[] parts = payload.split(":");
            if (parts.length != 3 || now().getEpochSecond() > Long.parseLong(parts[2])) {
                return null;
            }
            User user = repository.findUser(Long.parseLong(parts[0]));
            String digest = user == null ? null : user.getPasswordDigest();
            return digest != null && digest.substring(digest.length() - 10).equals(parts[1]) ? user : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
