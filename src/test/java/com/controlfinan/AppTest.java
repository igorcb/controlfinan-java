package com.controlfinan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.controlfinan.model.Entry;
import com.controlfinan.model.Repository;
import com.controlfinan.model.User;
import com.controlfinan.web.Mailer;
import com.controlfinan.web.Web;

/** Base for the tests: the real app on a random port, a real Postgres database emptied before each test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/controlfinan_java_test}",
    "controlfinan.secret=test-secret", "controlfinan.bcrypt-cost=4"})
@Import(AppTest.Config.class)
abstract class AppTest {
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    /** Keeps the sent e-mails instead of logging them. */
    static class RecordingMailer extends Mailer {
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(String to, String subject, String text) {
            sent.add(to + "|" + subject + "|" + text);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RecordingMailer recordingMailer() {
            return new RecordingMailer();
        }
    }

    record Response(int status, String body, String location) {
    }

    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Repository repository;
    @Autowired
    Web web;
    @Autowired
    RecordingMailer mailer;

    CookieManager cookies;
    HttpClient client;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE sessions, current_accounts, users RESTART IDENTITY CASCADE");
        mailer.sent.clear();
        web.clearRateLimits();
        travelTo(LocalDate.of(2026, 10, 27).atTime(12, 0).atZone(ZONE).toInstant());
        cookies = new CookieManager();
        client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    void travelTo(Instant instant) {
        web.setClock(Clock.fixed(instant, ZONE));
    }

    Response get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    /** Sends a form like the browser does, with the CSRF token from the cookie. */
    Response post(String path, Map<String, String> form) {
        Map<String, String> data = new LinkedHashMap<>(form);
        if (!data.containsKey("authenticity_token")) {
            if (csrfCookie() == null) {
                get("/passwords/new"); // any page with a form sets the CSRF cookie
            }
            data.put("authenticity_token", csrfCookie());
        }
        String body = data.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    String csrfCookie() {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(Web.CSRF_COOKIE))
            .map(HttpCookie::getValue).findFirst().orElse(null);
    }

    String baseUrl() {
        return "http://localhost:" + port;
    }

    private URI uri(String path) {
        return URI.create(baseUrl() + path);
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String location = response.headers().firstValue("Location").orElse("").replace(baseUrl(), "");
            return new Response(response.statusCode(), response.body(), location);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    User createUser(String email, String password) {
        User user = new User(0, email, web.hashPassword(password));
        repository.createUser(user);
        return user;
    }

    /** Creates a user and signs in through the real login form. */
    User signIn() {
        User user = createUser("driver@example.com", "secret123");
        Response response = post("/session", Map.of("email_address", "driver@example.com", "password", "secret123"));
        assertEquals(302, response.status(), "sign in");
        return user;
    }

    Entry createEntry(User user, String date, int type, int cost, String price, String historic) {
        Entry entry = new Entry();
        entry.setUserId(user.getId());
        entry.setCashAccountId(5);
        entry.setCostId(cost);
        entry.setTypeLaunche(type);
        entry.setDateOcurrence(LocalDate.parse(date));
        entry.setPrice(new BigDecimal(price));
        entry.setHistoric(historic);
        repository.saveEntry(entry);
        return entry;
    }

    Entry createCredit(User user, String date, String price) {
        return createEntry(user, date, Entry.CREDIT, Entry.RIDES, price, null);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    static void assertContains(String body, String... texts) {
        for (String text : texts) {
            assertTrue(body.contains(text), "expected body to contain: " + text);
        }
    }

    static void assertNotContains(String body, String... texts) {
        for (String text : texts) {
            if (body.contains(text)) {
                fail("expected body not to contain: " + text);
            }
        }
    }

    static void assertInOrder(String body, String... texts) {
        int from = 0;
        for (String text : texts) {
            int index = body.indexOf(text, from);
            if (index < 0) {
                fail("expected in order, missing after position " + from + ": " + text);
            }
            from = index + text.length();
        }
    }
}
