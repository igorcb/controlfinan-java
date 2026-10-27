package com.controlfinan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.controlfinan.model.Format;

class FormatTest {
    @Test
    void currencyFormatsLikeRailsInPortuguese() {
        assertEquals("R$ 1.234,50", Format.currency(new BigDecimal("1234.5")));
        assertEquals("-R$ 12,50", Format.currency(new BigDecimal("-12.5")));
        assertEquals("R$ 0,00", Format.currency(BigDecimal.ZERO));
        assertEquals("R$ 1.234.567,89", Format.currency(new BigDecimal("1234567.89")));
    }

    @Test
    void decimalTextPrintsLikeRubyBigDecimal() {
        assertEquals("150.0", Format.decimalText(new BigDecimal("150.00")));
        assertEquals("12.5", Format.decimalText(new BigDecimal("12.50")));
        assertEquals("0.05", Format.decimalText(new BigDecimal("0.05")));
        assertEquals("0.0", Format.decimalText(new BigDecimal("0.00")));
        assertEquals("", Format.decimalText(null));
    }
}
