package com.controlfinan.model;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/** SQL for users, sessions and entries (same schema as the Rails app). */
@Component
public class Repository {
    // Same regexp as Ruby's URI::MailTo::EMAIL_REGEXP.
    private static final Pattern EMAIL = Pattern.compile(
        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");
    private static final String ENTRY_COLUMNS = "id, user_id, cash_account_id, cost_id, type_launche, date_ocurrence, price, historic";
    private static final RowMapper<User> USER = (rs, n) -> new User(rs.getLong("id"), rs.getString("email_address"), rs.getString("password_digest"));
    private static final RowMapper<Entry> ENTRY = Repository::mapEntry;

    private final JdbcTemplate jdbc;

    public Repository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Users

    public User findUserByEmail(String email) {
        return jdbc.query("SELECT id, email_address, password_digest FROM users WHERE email_address = ?", USER, User.normalizeEmail(email))
            .stream().findFirst().orElse(null);
    }

    public User findUser(long id) {
        return jdbc.query("SELECT id, email_address, password_digest FROM users WHERE id = ?", USER, id).stream().findFirst().orElse(null);
    }

    public void createUser(User user) {
        user.setId(jdbc.queryForObject("INSERT INTO users (email_address, password_digest, created_at, updated_at) VALUES (?, ?, now(), now()) RETURNING id",
            Long.class, user.getEmailAddress(), user.getPasswordDigest()));
    }

    public void updatePassword(User user) {
        jdbc.update("UPDATE users SET password_digest = ?, updated_at = now() WHERE id = ?", user.getPasswordDigest(), user.getId());
    }

    /** The Rails messages (pt-BR) in the same order. */
    public List<String> validateUser(User user, String password, String confirmation, boolean passwordRequired) {
        List<String> errors = new ArrayList<>();
        if (passwordRequired && (password == null || password.isEmpty())) {
            errors.add("Senha não pode ficar em branco");
        }
        if (password != null && confirmation != null && !password.equals(confirmation)) {
            errors.add("Confirmação da senha não é igual a Senha");
        }
        user.setEmailAddress(User.normalizeEmail(user.getEmailAddress()));
        if (user.getEmailAddress().isEmpty()) {
            errors.add("E-mail não pode ficar em branco");
        } else if (!EMAIL.matcher(user.getEmailAddress()).matches()) {
            errors.add("E-mail não é válido");
        } else if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE email_address = ? AND id <> ?)",
            Boolean.class, user.getEmailAddress(), user.getId()))) {
            errors.add("E-mail já está em uso");
        }
        if (password != null && !password.isEmpty() && password.codePointCount(0, password.length()) < 6) {
            errors.add("Senha é muito curto (mínimo: 6 caracteres)");
        }
        return errors;
    }

    // Sessions

    public long createSession(long userId, String userAgent, String ipAddress) {
        return jdbc.queryForObject("INSERT INTO sessions (user_id, user_agent, ip_address, created_at, updated_at) VALUES (?, ?, ?, now(), now()) RETURNING id",
            Long.class, userId, userAgent, ipAddress);
    }

    public User findUserBySession(long sessionId) {
        return jdbc.query("SELECT u.id, u.email_address, u.password_digest FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.id = ?",
            USER, sessionId).stream().findFirst().orElse(null);
    }

    public void deleteSession(long sessionId) {
        jdbc.update("DELETE FROM sessions WHERE id = ?", sessionId);
    }

    public void deleteSessionsOf(long userId) {
        jdbc.update("DELETE FROM sessions WHERE user_id = ?", userId);
    }

    // Entries

    /** Up to limit entries, most recent first, optionally on one day. */
    public List<Entry> listEntries(long userId, LocalDate day, int limit, int offset) {
        if (day == null) {
            return jdbc.query("SELECT " + ENTRY_COLUMNS + " FROM current_accounts WHERE user_id = ? ORDER BY date_ocurrence DESC, id DESC LIMIT ? OFFSET ?",
                ENTRY, userId, limit, offset);
        }
        return jdbc.query("SELECT " + ENTRY_COLUMNS + " FROM current_accounts WHERE user_id = ? AND date_ocurrence = ? ORDER BY date_ocurrence DESC, id DESC LIMIT ? OFFSET ?",
            ENTRY, userId, Date.valueOf(day), limit, offset);
    }

    /** Credits minus debits: the sign comes from type_launche (-1 or 1). */
    public BigDecimal balance(long userId, LocalDate day) {
        if (day == null) {
            return jdbc.queryForObject("SELECT COALESCE(SUM(price * type_launche), 0) FROM current_accounts WHERE user_id = ?", BigDecimal.class, userId);
        }
        return jdbc.queryForObject("SELECT COALESCE(SUM(price * type_launche), 0) FROM current_accounts WHERE user_id = ? AND date_ocurrence = ?",
            BigDecimal.class, userId, Date.valueOf(day));
    }

    public Entry findEntry(long userId, long id) {
        return jdbc.query("SELECT " + ENTRY_COLUMNS + " FROM current_accounts WHERE user_id = ? AND id = ?", ENTRY, userId, id)
            .stream().findFirst().orElse(null);
    }

    public void saveEntry(Entry e) {
        if (e.isPersisted()) {
            jdbc.update("UPDATE current_accounts SET cash_account_id = ?, cost_id = ?, type_launche = ?, date_ocurrence = ?, price = ?, historic = ?, "
                + "updated_at = now() WHERE id = ? AND user_id = ?", e.getCashAccountId(), e.getCostId(), e.getTypeLaunche(),
                Date.valueOf(e.getDateOcurrence()), e.getPrice(), e.getHistoric(), e.getId(), e.getUserId());
            return;
        }
        e.setId(jdbc.queryForObject("INSERT INTO current_accounts (user_id, cash_account_id, cost_id, type_launche, date_ocurrence, price, historic, "
            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, now(), now()) RETURNING id", Long.class, e.getUserId(), e.getCashAccountId(),
            e.getCostId(), e.getTypeLaunche(), Date.valueOf(e.getDateOcurrence()), e.getPrice(), e.getHistoric()));
    }

    public void deleteEntry(Entry e) {
        jdbc.update("DELETE FROM current_accounts WHERE id = ? AND user_id = ?", e.getId(), e.getUserId());
    }

    /** [date, balance] of each day of the month, most recent first. */
    public Map<LocalDate, BigDecimal> balanceByDay(long userId, LocalDate first, LocalDate last) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query("SELECT date_ocurrence, SUM(price * type_launche) AS total FROM current_accounts WHERE user_id = ? AND date_ocurrence BETWEEN ? AND ? "
            + "GROUP BY date_ocurrence ORDER BY date_ocurrence DESC", rs -> {
                result.put(rs.getDate("date_ocurrence").toLocalDate(), rs.getBigDecimal("total"));
            }, userId, Date.valueOf(first), Date.valueOf(last));
        return result;
    }

    /** [cost_id, sum of price] of the given categories (fixed ids from the code, never user input). */
    public Map<Integer, BigDecimal> totalByCost(long userId, LocalDate first, LocalDate last, List<Integer> costIds) {
        Map<Integer, BigDecimal> result = new LinkedHashMap<>();
        String ids = String.join(", ", costIds.stream().map(String::valueOf).toList());
        jdbc.query("SELECT cost_id, SUM(price) AS total FROM current_accounts WHERE user_id = ? AND date_ocurrence BETWEEN ? AND ? "
            + "AND cost_id IN (" + ids + ") GROUP BY cost_id", rs -> {
                result.put(rs.getInt("cost_id"), rs.getBigDecimal("total"));
            }, userId, Date.valueOf(first), Date.valueOf(last));
        return result;
    }

    public BigDecimal ridesBalance(long userId, LocalDate first, LocalDate last) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(price * type_launche), 0) FROM current_accounts WHERE user_id = ? "
            + "AND date_ocurrence BETWEEN ? AND ? AND cost_id = ?", BigDecimal.class, userId, Date.valueOf(first), Date.valueOf(last), Entry.RIDES);
    }

    private static Entry mapEntry(ResultSet rs, int n) throws SQLException {
        Entry e = new Entry();
        e.setId(rs.getLong("id"));
        e.setUserId(rs.getLong("user_id"));
        e.setCashAccountId(rs.getInt("cash_account_id"));
        e.setCostId(rs.getInt("cost_id"));
        e.setTypeLaunche(rs.getInt("type_launche"));
        e.setDateOcurrence(rs.getDate("date_ocurrence").toLocalDate());
        e.setPrice(rs.getBigDecimal("price"));
        e.setHistoric(rs.getString("historic"));
        return e;
    }
}
