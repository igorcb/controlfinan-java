package com.controlfinan.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Formatting that matches Rails' pt-BR helpers (number_to_currency, l(date)) and BigDecimal#to_s. */
public final class Format {
    public static final String[] MONTHS = {"janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto",
        "setembro", "outubro", "novembro", "dezembro"};
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    /** Passed to the templates as "F": Thymeleaf calls the static methods through it without looking the class up by name. */
    public static final Format INSTANCE = new Format();

    private Format() {
    }

    /** "R$ 1.234,50"; negatives as "-R$ 1.234,50". */
    public static String currency(BigDecimal value) {
        BigDecimal amount = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        String sign = amount.signum() < 0 ? "-" : "";
        String[] parts = amount.abs().toPlainString().split("\\.");
        StringBuilder units = new StringBuilder();
        for (int i = 0; i < parts[0].length(); i++) {
            if (i > 0 && (parts[0].length() - i) % 3 == 0) {
                units.append('.');
            }
            units.append(parts[0].charAt(i));
        }
        return sign + "R$ " + units + "," + parts[1];
    }

    public static String amountClass(BigDecimal value) {
        return value != null && value.signum() < 0 ? "text-red-700" : "text-emerald-700";
    }

    /** How Ruby prints a BigDecimal: 150 -> "150.0", 12.50 -> "12.5". */
    public static String decimalText(BigDecimal value) {
        if (value == null) {
            return "";
        }
        String text = value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
        return text.contains(".") ? text : text + ".0";
    }

    public static String brDate(LocalDate date) {
        return date == null ? "" : date.format(BR_DATE);
    }

    public static String dayMonth(LocalDate date) {
        return date.format(DAY_MONTH);
    }
}
