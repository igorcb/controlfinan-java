package com.controlfinan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.controlfinan.model.Entry;
import com.controlfinan.model.User;

class RequestsTest extends AppTest {
    @Test
    void loginFormIsShown() {
        Response response = get("/session/new");
        assertEquals(200, response.status());
        assertContains(response.body(), "Esqueceu a senha?");
    }

    @Test
    void guestIsRedirectedToLoginAndBackAfterSigningIn() {
        createUser("driver@example.com", "secret123");
        assertEquals("/session/new", get("/dashboard").location());

        Response response = post("/session", Map.of("email_address", " Driver@Example.com ", "password", "secret123"));

        assertEquals("/dashboard", response.location());
        assertEquals(1, count("SELECT count(*) FROM sessions"));
        assertEquals(200, get("/dashboard").status());
    }

    @Test
    void wrongPasswordIsRejected() {
        createUser("driver@example.com", "secret123");
        assertEquals("/session/new", post("/session", Map.of("email_address", "driver@example.com", "password", "wrong")).location());
        assertContains(get("/session/new").body(), "E-mail ou senha inválidos.");
    }

    @Test
    void loginAttemptsAreLimitedToTenEveryThreeMinutes() {
        Map<String, String> form = Map.of("email_address", "x@example.com", "password", "x");
        for (int i = 0; i < 10; i++) {
            post("/session", form);
        }
        post("/session", form);
        assertContains(get("/session/new").body(), "Tente novamente mais tarde.");

        travelTo(web.now().plus(Duration.ofMinutes(4)));
        post("/session", form);
        assertContains(get("/session/new").body(), "E-mail ou senha inválidos.");
    }

    @Test
    void signOutDeletesTheSession() {
        signIn();
        Response response = post("/session", Map.of("_method", "delete"));
        assertEquals(303, response.status());
        assertEquals("/session/new", response.location());
        assertEquals(0, count("SELECT count(*) FROM sessions"));
        assertEquals("/session/new", get("/").location());
    }

    @Test
    void formsWithoutAValidCsrfTokenAreRejected() {
        createUser("driver@example.com", "secret123");
        Response response = post("/session", Map.of("authenticity_token", "forged", "email_address", "driver@example.com", "password", "secret123"));
        assertEquals(422, response.status());
    }

    @Test
    void registrationCreatesAnAccountAndSignsIn() {
        Response response = post("/registration", Map.of("user[email_address]", " New@Example.com ", "user[password]", "secret123",
            "user[password_confirmation]", "secret123"));
        assertEquals("/", response.location());
        assertEquals(1, count("SELECT count(*) FROM users WHERE email_address = 'new@example.com'"));
        assertContains(get("/").body(), "Conta criada.");
    }

    @Test
    void registrationShowsTheValidationMessages() {
        Response response = post("/registration", Map.of("user[email_address]", "bad", "user[password]", "1", "user[password_confirmation]", "2"));
        assertEquals(422, response.status());
        assertContains(response.body(), "Confirmação da senha não é igual a Senha", "E-mail não é válido");
    }

    @Test
    void passwordResetEmailDoesNotRevealWhetherTheAddressExists() {
        createUser("driver@example.com", "secret123");
        String notice = "Se o e-mail estiver cadastrado, enviamos as instruções para redefinir a senha.";

        assertEquals("/session/new", post("/passwords", Map.of("email_address", "driver@example.com")).location());
        assertContains(get("/session/new").body(), notice);
        post("/passwords", Map.of("email_address", "missing@example.com"));
        assertContains(get("/session/new").body(), notice);

        assertEquals(1, mailer.sent.size());
        assertContains(mailer.sent.get(0), "driver@example.com|Redefinição de senha|", "/passwords/", "/edit", "15 minutos");
    }

    @Test
    void passwordResetChangesThePasswordAndEndsEverySession() {
        User user = signIn();
        String token = web.passwordResetToken(user);

        assertContains(get("/passwords/" + token + "/edit").body(), "Defina sua nova senha");
        Response response = post("/passwords/" + token, Map.of("_method", "put", "password", "new-secret", "password_confirmation", "new-secret"));

        assertEquals("/session/new", response.location());
        assertContains(get("/session/new").body(), "Senha redefinida.");
        assertEquals(0, count("SELECT count(*) FROM sessions"));
        assertTrue(web.checkPassword(repository.findUser(user.getId()), "new-secret"));
    }

    @Test
    void passwordResetRejectsPasswordsThatDoNotMatch() {
        String token = web.passwordResetToken(createUser("driver@example.com", "secret123"));
        Response response = post("/passwords/" + token, Map.of("_method", "put", "password", "new-secret", "password_confirmation", "other"));
        assertEquals("/passwords/" + token + "/edit", response.location());
        assertContains(get(response.location()).body(), "As senhas não conferem ou têm menos de 6 caracteres.");
    }

    @Test
    void passwordResetTokenExpiresAfterFifteenMinutesAndAfterAPasswordChange() {
        User user = createUser("driver@example.com", "secret123");
        String token = web.passwordResetToken(user);

        travelTo(web.now().plus(Duration.ofMinutes(16)));
        assertEquals("/passwords/new", get("/passwords/" + token + "/edit").location());
        assertContains(get("/passwords/new").body(), "O link de redefinição é inválido ou expirou.");

        travelTo(web.now().minus(Duration.ofMinutes(16)));
        user.setPasswordDigest(web.hashPassword("another123"));
        repository.updatePassword(user);
        assertEquals("/passwords/new", get("/passwords/" + token + "/edit").location());
        assertEquals("/passwords/new", get("/passwords/invalid/edit").location());
    }

    @Test
    void listShowsEntriesAndTheTotalBalance() {
        User user = signIn();
        createCredit(user, "2026-10-27", "150.00");
        createEntry(user, "2026-10-27", Entry.DEBIT, 0, "40.00", null);
        assertContains(get("/").body(), "Saldo total", "R$ 110,00", "-R$ 40,00");
    }

    @Test
    void listFiltersByDay() {
        User user = signIn();
        createCredit(user, "2026-10-26", "10.00");
        createCredit(user, "2026-10-27", "20.00");
        String body = get("/current_accounts?date=2026-10-26").body();
        assertContains(body, "Saldo do dia 26/10/2026", "R$ 10,00");
        assertNotContains(body, "R$ 20,00");
    }

    @Test
    void listPaginatesThirtyEntriesPerPage() {
        User user = signIn();
        for (int n = 1; n <= 31; n++) {
            createEntry(user, "2026-10-27", Entry.CREDIT, Entry.RIDES, "150.00", "entry " + n + ".");
        }
        String first = get("/current_accounts").body();
        assertContains(first, "Mais antigos →");
        assertNotContains(first, "← Mais recentes", "entry 1.");
        String second = get("/current_accounts?page=2").body();
        assertContains(second, "← Mais recentes", "entry 1.");
        assertNotContains(second, "Mais antigos →");
    }

    @Test
    void jsonHasTheJbuilderFields() {
        User user = signIn();
        Entry entry = createEntry(user, "2026-10-27", Entry.CREDIT, Entry.RIDES, "150.00", "Café/Almoço");
        String expected = "{\"id\":" + entry.getId() + ",\"cash_account_id\":5,\"cost_id\":6,\"date_ocurrence\":\"2026-10-27\",\"price\":\"150.0\","
            + "\"historic\":\"Café/Almoço\",\"user_id\":" + user.getId() + ",\"type_launche\":1,\"url\":\"" + baseUrl() + "/current_accounts/"
            + entry.getId() + ".json\"}";
        assertEquals("[" + expected + "]", get("/current_accounts.json").body());
        assertEquals(expected, get("/current_accounts/" + entry.getId() + ".json").body());
    }

    @Test
    void otherUsersEntriesAreNotFound() {
        signIn();
        Entry other = createCredit(createUser("other@example.com", "secret123"), "2026-10-27", "150.00");
        String path = "/current_accounts/" + other.getId();
        assertEquals(404, get(path).status());
        assertEquals(404, get(path + "/edit").status());
        assertEquals(404, post(path, Map.of("_method", "delete")).status());
        assertEquals(1, count("SELECT count(*) FROM current_accounts"));
    }

    static Map<String, String> validForm() {
        Map<String, String> form = new HashMap<>();
        form.put("current_account[type_launche]", "credito");
        form.put("current_account[price]", "150");
        form.put("current_account[date_ocurrence]", "2026-10-27");
        form.put("current_account[cost_id]", "6");
        form.put("current_account[cash_account_id]", "5");
        form.put("current_account[historic]", "Corridas do dia");
        return form;
    }

    @Test
    void createEntry() {
        User user = signIn();
        assertEquals("/current_accounts", post("/current_accounts", validForm()).location());
        assertContains(get("/current_accounts").body(), "Lançamento cadastrado.");
        assertEquals(1, count("SELECT count(*) FROM current_accounts WHERE user_id = ? AND price = 150 AND type_launche = 1", user.getId()));
    }

    @Test
    void createEntryIgnoresAUserIdSentInTheForm() {
        User user = signIn();
        User other = createUser("other@example.com", "secret123");
        Map<String, String> form = validForm();
        form.put("current_account[user_id]", String.valueOf(other.getId()));
        post("/current_accounts", form);
        assertEquals(1, count("SELECT count(*) FROM current_accounts WHERE user_id = ?", user.getId()));
    }

    @Test
    void createEntryShowsTheValidationMessages() {
        signIn();
        Response response = post("/current_accounts", Map.of("current_account[price]", ""));
        assertEquals(422, response.status());
        assertContains(response.body(), "Valor não pode ficar em branco", "Tipo não está incluído na lista");
    }

    @Test
    void updateEntry() {
        Entry entry = createCredit(signIn(), "2026-10-27", "150.00");
        Map<String, String> form = validForm();
        form.put("_method", "patch");
        form.put("current_account[price]", "12.5");
        Response response = post("/current_accounts/" + entry.getId(), form);
        assertEquals("/current_accounts/" + entry.getId(), response.location());
        assertContains(get(response.location()).body(), "Lançamento atualizado.", "R$ 12,50");
    }

    @Test
    void deleteEntry() {
        Entry entry = createCredit(signIn(), "2026-10-27", "150.00");
        Response response = post("/current_accounts/" + entry.getId(), Map.of("_method", "delete"));
        assertEquals(303, response.status());
        assertContains(get("/current_accounts").body(), "Lançamento removido.");
        assertEquals(0, count("SELECT count(*) FROM current_accounts"));
    }

    @Test
    void legacyDailySearchRedirectsToTheFilter() {
        Response response = get("/search_account?date_calendar_now=2026-10-27");
        assertEquals(301, response.status());
        assertEquals("/current_accounts?date=2026-10-27", response.location());
    }

    @Test
    void dashboardShowsTheDailyAndMonthlyBalances() {
        User user = signIn();
        createCredit(user, "2026-10-02", "200.00");
        createEntry(user, "2026-10-02", Entry.DEBIT, 0, "50.00", null);
        createEntry(user, "2026-10-05", Entry.DEBIT, 8, "20.00", null);
        createCredit(user, "2026-09-30", "999.00");

        String body = get("/dashboard?month=2026-10").body();

        assertContains(body, "Outubro de 2026", "Saldo Diário", "Saldo Mensal");
        assertInOrder(body, "05/10/2026", "-R$ 20,00", "02/10/2026", "R$ 150,00", "Total do mês", "R$ 130,00");
        assertInOrder(body, "Corridas", "R$ 200,00", "Combustível", "-R$ 50,00", "Lavagem", "-R$ 20,00", "Telefone", "R$ 0,00", "Saldo do mês", "R$ 130,00");
        assertNotContains(body, "999");
    }

    @Test
    void dashboardLinksToThePreviousAndNextMonths() {
        signIn();
        assertContains(get("/dashboard?month=2026-01").body(), "/dashboard?month=2025-12", "/dashboard?month=2026-02");
    }

    @Test
    void dashboardFallsBackToTheCurrentMonth() {
        signIn();
        travelTo(LocalDate.of(2026, 3, 15).atTime(12, 0).atZone(ZONE).toInstant());
        assertContains(get("/dashboard?month=bad").body(), "Março de 2026");
    }
}
