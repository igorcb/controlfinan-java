package com.controlfinan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.controlfinan.model.Entry;
import com.controlfinan.model.User;

class ModelsTest extends AppTest {
    @Test
    void passwordUsesTheRailsBcryptFormat() {
        User user = new User(0, "a@example.com", web.hashPassword("secret123"));
        assertTrue(user.getPasswordDigest().startsWith("$2a$"));
        assertTrue(web.checkPassword(user, "secret123"));
        assertFalse(web.checkPassword(user, "wrong"));
    }

    @Test
    void acceptsADigestWrittenByRails() {
        // Digest of "benchmark123" written by the Rails app (has_secure_password, bcrypt-ruby) for its benchmark account.
        User user = new User(0, "benchmark@exemplo.com", "$2a$12$xUBQqXOC4yO3ZUCU3U./FeJ/CU4pqmbNxFQ5iUUrWzLWmKSpUKChe");
        assertTrue(web.checkPassword(user, "benchmark123"));
        assertFalse(web.checkPassword(user, "wrong"));
    }

    @Test
    void validateUserReturnsTheRailsMessages() {
        createUser("taken@example.com", "secret123");
        assertEquals(List.of("Senha não pode ficar em branco", "E-mail não pode ficar em branco"),
            repository.validateUser(new User(), "", "", true));
        assertEquals(List.of("Confirmação da senha não é igual a Senha", "E-mail não é válido", "Senha é muito curto (mínimo: 6 caracteres)"),
            repository.validateUser(new User(0, "bad", null), "123", "456", true));
        assertEquals(List.of("E-mail já está em uso"), repository.validateUser(new User(0, " TAKEN@example.com ", null), "secret123", "secret123", true));
    }

    @Test
    void entryValidateReturnsTheRailsMessages() {
        assertEquals(List.of("Tipo não está incluído na lista", "Conta não está incluído na lista", "Categoria não está incluído na lista",
            "Data não pode ficar em branco", "Valor não pode ficar em branco", "Valor não é um número"), new Entry().validate());

        Entry zero = new Entry();
        zero.setCashAccountId(0);
        zero.setCostId(0);
        zero.setTypeLaunche(Entry.DEBIT);
        zero.setDateOcurrence(LocalDate.now());
        zero.setPrice(BigDecimal.ZERO);
        assertEquals(List.of("Valor deve ser maior que 0"), zero.validate());
    }

    @Test
    void entryNamesWithFallback() {
        Entry entry = new Entry();
        entry.setCashAccountId(1);
        entry.setCostId(99);
        entry.setTypeLaunche(Entry.DEBIT);
        entry.setPrice(new BigDecimal("10.00"));
        assertEquals("Itaú", entry.nameAccount());
        assertEquals("Não definido", entry.nameCost());
        assertEquals("Débito", entry.typeName());
        assertEquals(new BigDecimal("-10.00"), entry.signedPrice());
    }

    @Test
    void balanceIsCreditsMinusDebits() {
        User user = createUser("driver@example.com", "secret123");
        createCredit(user, "2026-10-27", "150.00");
        createEntry(user, "2026-10-27", Entry.DEBIT, 0, "40.50", null);
        assertEquals(0, new BigDecimal("109.50").compareTo(repository.balance(user.getId(), null)));
    }

    @Test
    void balanceByDayIsMostRecentFirst() {
        User user = createUser("driver@example.com", "secret123");
        createCredit(user, "2026-10-01", "10.00");
        createCredit(user, "2026-10-02", "20.00");
        createEntry(user, "2026-10-02", Entry.DEBIT, 0, "5.00", null);
        Map<LocalDate, BigDecimal> days = repository.balanceByDay(user.getId(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(List.of(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)), List.copyOf(days.keySet()));
        assertEquals(0, new BigDecimal("15").compareTo(days.get(LocalDate.of(2026, 10, 2))));
    }
}
