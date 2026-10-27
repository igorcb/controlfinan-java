package com.controlfinan.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A row of current_accounts (a credit or a debit). */
public class Entry {
    public static final List<Option> CASH_ACCOUNTS = List.of(
        new Option(0, "Conta Dinheiro"), new Option(1, "Itaú"), new Option(2, "Santander"), new Option(3, "CEF Poupança"),
        new Option(4, "PagSeguro"), new Option(5, "Uber"), new Option(6, "Platinum Itaú"), new Option(7, "Banco do Brasil"),
        new Option(8, "99Pop"));
    public static final List<Option> COSTS = List.of(
        new Option(0, "Combustível"), new Option(1, "Central"), new Option(2, "Aluguel Carro"), new Option(3, "Almoço"),
        new Option(4, "Lanche"), new Option(5, "Diversos"), new Option(6, "Corridas"), new Option(7, "Telefone"),
        new Option(8, "Lavagem"), new Option(9, "Transferência"), new Option(10, "Depósitos"), new Option(11, "Estacionamento"),
        new Option(12, "Impostos"), new Option(13, "Multas"), new Option(14, "Manutenção"), new Option(15, "Viagens Uber"));
    public static final int DEBIT = -1;
    public static final int CREDIT = 1;
    public static final int RIDES = 6;
    /** Ride expenses discounted from the rides in the monthly balance (legacy dashboard rule), in this order. */
    public static final List<Option> RIDE_EXPENSES = List.of(new Option(0, "Combustível"), new Option(8, "Lavagem"), new Option(7, "Telefone"));

    private Long id;
    private long userId;
    private Integer cashAccountId;
    private Integer costId;
    private Integer typeLaunche;
    private LocalDate dateOcurrence;
    private BigDecimal price;
    /** Raw price typed in the form, used by the validation like Rails numericality. */
    private String priceText = "";
    private String historic;

    public boolean isPersisted() {
        return id != null;
    }

    public boolean isCredit() {
        return typeLaunche != null && typeLaunche == CREDIT;
    }

    public String nameAccount() {
        return optionName(CASH_ACCOUNTS, cashAccountId);
    }

    public String nameCost() {
        return optionName(COSTS, costId);
    }

    public String typeName() {
        return isCredit() ? "Crédito" : "Débito";
    }

    public BigDecimal signedPrice() {
        return isCredit() ? price : price.negate();
    }

    /** The value shown in the form: the decimal like Rails, or what the user typed. */
    public String priceField() {
        return price != null ? Format.decimalText(price) : priceText;
    }

    /** The Rails messages (pt-BR) in the same order as the model validations. */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (typeLaunche == null || (typeLaunche != DEBIT && typeLaunche != CREDIT)) {
            errors.add("Tipo não está incluído na lista");
        }
        if (!hasOption(CASH_ACCOUNTS, cashAccountId)) {
            errors.add("Conta não está incluído na lista");
        }
        if (!hasOption(COSTS, costId)) {
            errors.add("Categoria não está incluído na lista");
        }
        if (dateOcurrence == null) {
            errors.add("Data não pode ficar em branco");
        }
        // Like Rails presence + numericality: a blank value is also "not a number".
        if (price == null && priceText.isEmpty()) {
            errors.add("Valor não pode ficar em branco");
            errors.add("Valor não é um número");
        } else if (price == null) {
            errors.add("Valor não é um número");
        } else if (price.signum() <= 0) {
            errors.add("Valor deve ser maior que 0");
        }
        return errors;
    }

    private static String optionName(List<Option> options, Integer id) {
        return options.stream().filter(o -> id != null && o.id() == id).map(Option::name).findFirst().orElse("Não definido");
    }

    private static boolean hasOption(List<Option> options, Integer id) {
        return id != null && options.stream().anyMatch(o -> o.id() == id);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Integer getCashAccountId() {
        return cashAccountId;
    }

    public void setCashAccountId(Integer cashAccountId) {
        this.cashAccountId = cashAccountId;
    }

    public Integer getCostId() {
        return costId;
    }

    public void setCostId(Integer costId) {
        this.costId = costId;
    }

    public Integer getTypeLaunche() {
        return typeLaunche;
    }

    public void setTypeLaunche(Integer typeLaunche) {
        this.typeLaunche = typeLaunche;
    }

    public LocalDate getDateOcurrence() {
        return dateOcurrence;
    }

    public void setDateOcurrence(LocalDate dateOcurrence) {
        this.dateOcurrence = dateOcurrence;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getPriceText() {
        return priceText;
    }

    public void setPriceText(String priceText) {
        this.priceText = priceText == null ? "" : priceText;
    }

    public String getHistoric() {
        return historic;
    }

    public void setHistoric(String historic) {
        this.historic = historic;
    }
}
