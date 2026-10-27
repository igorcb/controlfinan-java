package com.controlfinan.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.controlfinan.model.Entry;
import com.controlfinan.model.Format;
import com.controlfinan.model.Option;
import com.controlfinan.model.Repository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class DashboardsController {
    private static final Pattern MONTH = Pattern.compile("(\\d{4})-(\\d{1,2})");

    /** A label and an amount in the dashboard tables. */
    public record Row(String label, BigDecimal amount) {
    }

    /** The data of one dashboard table. */
    public record Table(String header, List<Row> rows, String totalLabel, BigDecimal total) {
    }

    private final Web web;
    private final Repository repository;

    public DashboardsController(Web web, Repository repository) {
        this.web = web;
        this.repository = repository;
    }

    @GetMapping("/dashboard")
    public String show(@RequestParam(required = false) String month, Model model, HttpServletRequest request, HttpServletResponse response) {
        long userId = Web.currentUser(request).getId();
        LocalDate first = parseMonth(month);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());

        List<Row> daily = new ArrayList<>();
        BigDecimal monthBalance = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> day : repository.balanceByDay(userId, first, last).entrySet()) {
            daily.add(new Row(Format.brDate(day.getKey()), day.getValue()));
            monthBalance = monthBalance.add(day.getValue());
        }

        Map<Integer, BigDecimal> expenses = repository.totalByCost(userId, first, last, Entry.RIDE_EXPENSES.stream().map(Option::id).toList());
        BigDecimal rides = repository.ridesBalance(userId, first, last);
        List<Row> monthly = new ArrayList<>(List.of(new Row("Corridas", rides)));
        BigDecimal ridesNet = rides;
        for (Option expense : Entry.RIDE_EXPENSES) {
            BigDecimal amount = expenses.getOrDefault(expense.id(), BigDecimal.ZERO);
            monthly.add(new Row(expense.name(), amount.negate()));
            ridesNet = ridesNet.subtract(amount);
        }

        String label = Format.MONTHS[first.getMonthValue() - 1] + " de " + first.getYear();
        model.addAttribute("nav", "dashboard");
        model.addAttribute("monthLabel", label.substring(0, 1).toUpperCase() + label.substring(1));
        model.addAttribute("previousMonth", first.minusMonths(1).toString().substring(0, 7));
        model.addAttribute("nextMonth", first.plusMonths(1).toString().substring(0, 7));
        model.addAttribute("today", web.today().toString());
        model.addAttribute("daily", new Table("Data", daily, "Total do mês", monthBalance));
        model.addAttribute("monthly", new Table("Item", monthly, "Saldo do mês", ridesNet));
        return web.render("dashboards/show", model, request, response);
    }

    private LocalDate parseMonth(String value) {
        if (value != null) {
            Matcher match = MONTH.matcher(value);
            if (match.matches()) {
                int month = Integer.parseInt(match.group(2));
                if (month >= 1 && month <= 12) {
                    return LocalDate.of(Integer.parseInt(match.group(1)), month, 1);
                }
            }
        }
        return web.today().withDayOfMonth(1);
    }
}
