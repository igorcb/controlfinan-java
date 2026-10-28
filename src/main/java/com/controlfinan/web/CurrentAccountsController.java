package com.controlfinan.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import com.controlfinan.model.Entry;
import com.controlfinan.model.Format;
import com.controlfinan.model.Repository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class CurrentAccountsController {
    private static final int PER_PAGE = 30;
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private final Web web;
    private final Repository repository;
    private final ObjectMapper json;

    public CurrentAccountsController(Web web, Repository repository, ObjectMapper json) {
        this.web = web;
        this.repository = repository;
        this.json = json;
    }

    @GetMapping({"/", "/current_accounts"})
    public String index(@RequestParam(required = false) String date, @RequestParam(required = false) String page, Model model,
                        HttpServletRequest request, HttpServletResponse response) {
        long userId = Web.currentUser(request).getId();
        LocalDate day = parseDate(date);
        int pageNumber = parsePage(page);
        List<Entry> entries = repository.listEntries(userId, day, PER_PAGE + 1, (pageNumber - 1) * PER_PAGE);
        boolean nextPage = entries.size() > PER_PAGE;
        String dateParam = day == null ? "" : "date=" + day + "&";
        model.addAttribute("nav", "index");
        model.addAttribute("entries", nextPage ? entries.subList(0, PER_PAGE) : entries);
        model.addAttribute("balance", repository.balance(userId, day));
        model.addAttribute("dateLabel", day == null ? "" : Format.brDate(day));
        model.addAttribute("dateValue", day == null ? "" : day.toString());
        model.addAttribute("previousUrl", "/current_accounts?" + dateParam + "page=" + (pageNumber - 1));
        model.addAttribute("nextUrl", "/current_accounts?" + dateParam + "page=" + (pageNumber + 1));
        model.addAttribute("page", pageNumber);
        model.addAttribute("nextPage", nextPage);
        return web.render("current_accounts/index", model, request, response);
    }

    @GetMapping("/current_accounts.json")
    public ResponseEntity<String> indexJson(@RequestParam(required = false) String date, @RequestParam(required = false) String page,
                                            HttpServletRequest request) throws JsonProcessingException {
        List<Entry> entries = repository.listEntries(Web.currentUser(request).getId(), parseDate(date), PER_PAGE, (parsePage(page) - 1) * PER_PAGE);
        return jsonResponse(entries.stream().map(e -> asJson(request, e)).toList());
    }

    @GetMapping("/current_accounts/{id}.json")
    public ResponseEntity<String> showJson(@PathVariable String id, HttpServletRequest request) throws JsonProcessingException {
        return jsonResponse(asJson(request, find(request, id)));
    }

    @GetMapping("/current_accounts/{id}")
    public String show(@PathVariable String id, Model model, HttpServletRequest request, HttpServletResponse response) {
        model.addAttribute("entry", find(request, id));
        return web.render("current_accounts/show", model, request, response);
    }

    @GetMapping("/current_accounts/new")
    public String newEntry(Model model, HttpServletRequest request, HttpServletResponse response) {
        Entry entry = new Entry();
        entry.setDateOcurrence(web.today());
        entry.setTypeLaunche(Entry.DEBIT);
        return form("current_accounts/new", entry, List.of(), model, request, response);
    }

    @GetMapping("/current_accounts/{id}/edit")
    public String edit(@PathVariable String id, Model model, HttpServletRequest request, HttpServletResponse response) {
        return form("current_accounts/edit", find(request, id), List.of(), model, request, response);
    }

    @PostMapping("/current_accounts")
    public String create(Model model, HttpServletRequest request, HttpServletResponse response) {
        Entry entry = new Entry();
        entry.setUserId(Web.currentUser(request).getId());
        List<String> errors = assign(entry, request);
        if (!errors.isEmpty()) {
            response.setStatus(422);
            return form("current_accounts/new", entry, errors, model, request, response);
        }
        repository.saveEntry(entry);
        return web.redirect(response, "/current_accounts", "notice", "Lançamento cadastrado.");
    }

    @RequestMapping(value = "/current_accounts/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public String update(@PathVariable String id, Model model, HttpServletRequest request, HttpServletResponse response) {
        Entry entry = find(request, id);
        List<String> errors = assign(entry, request);
        if (!errors.isEmpty()) {
            response.setStatus(422);
            return form("current_accounts/edit", entry, errors, model, request, response);
        }
        repository.saveEntry(entry);
        return web.redirect(response, "/current_accounts/" + entry.getId(), "notice", "Lançamento atualizado.");
    }

    @DeleteMapping("/current_accounts/{id}")
    public RedirectView destroy(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        repository.deleteEntry(find(request, id));
        web.redirect(response, "/current_accounts", "notice", "Lançamento removido.");
        return Redirects.seeOther("/current_accounts");
    }

    @GetMapping("/search_account")
    public RedirectView searchAccount(@RequestParam(name = "date_calendar_now", defaultValue = "") String date) {
        // Legacy daily balance search, now a filter on the listing.
        RedirectView view = new RedirectView("/current_accounts?date=" + date);
        view.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
        view.setExposeModelAttributes(false);
        return view;
    }

    private String form(String template, Entry entry, List<String> errors, Model model, HttpServletRequest request, HttpServletResponse response) {
        model.addAttribute("entry", entry);
        model.addAttribute("errors", errors);
        model.addAttribute("costs", Entry.COSTS);
        model.addAttribute("cashAccounts", Entry.CASH_ACCOUNTS);
        return web.render(template, model, request, response);
    }

    /** The signed in user's entry; other users' entries are "not found". */
    private Entry find(HttpServletRequest request, String id) {
        if (!INTEGER.matcher(id).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Entry entry = repository.findEntry(Web.currentUser(request).getId(), Long.parseLong(id));
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return entry;
    }

    /**
     * Copies the form fields present in the request into the entry and returns the validation messages.
     * Like Rails' update, keys absent from the request keep the entry's current value; a present
     * key (even empty) is assigned. On create the entry is new, so absent keys stay blank.
     */
    private static List<String> assign(Entry entry, HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        if (params.containsKey("current_account[cash_account_id]")) {
            entry.setCashAccountId(integer(request.getParameter("current_account[cash_account_id]")));
        }
        if (params.containsKey("current_account[cost_id]")) {
            entry.setCostId(integer(request.getParameter("current_account[cost_id]")));
        }
        if (params.containsKey("current_account[type_launche]")) {
            String type = request.getParameter("current_account[type_launche]");
            entry.setTypeLaunche("debito".equals(type) ? Integer.valueOf(Entry.DEBIT) : "credito".equals(type) ? Integer.valueOf(Entry.CREDIT) : null);
        }
        if (params.containsKey("current_account[date_ocurrence]")) {
            entry.setDateOcurrence(parseDate(request.getParameter("current_account[date_ocurrence]")));
        }
        if (params.containsKey("current_account[price]")) {
            String price = request.getParameter("current_account[price]");
            entry.setPriceText(price == null ? "" : price.strip());
            entry.setPrice(NUMBER.matcher(entry.getPriceText()).matches()
                ? new BigDecimal(entry.getPriceText()).setScale(2, RoundingMode.HALF_UP) : null);
        }
        if (params.containsKey("current_account[historic]")) {
            String historic = request.getParameter("current_account[historic]");
            entry.setHistoric(historic == null || historic.isEmpty() ? null : historic);
        }
        return entry.validate();
    }

    private static Integer integer(String value) {
        return value != null && INTEGER.matcher(value.strip()).matches() ? Integer.valueOf(value.strip()) : null;
    }

    static LocalDate parseDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int parsePage(String value) {
        try {
            return Math.max(Integer.parseInt(value), 1);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private Map<String, Object> asJson(HttpServletRequest request, Entry e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", e.getId());
        data.put("cash_account_id", e.getCashAccountId());
        data.put("cost_id", e.getCostId());
        data.put("date_ocurrence", e.getDateOcurrence().toString());
        data.put("price", Format.decimalText(e.getPrice()));
        data.put("historic", e.getHistoric());
        data.put("user_id", e.getUserId());
        data.put("type_launche", e.getTypeLaunche());
        data.put("url", Web.absoluteUrl(request, "/current_accounts/" + e.getId() + ".json"));
        return data;
    }

    /** Compact JSON like jbuilder. */
    private ResponseEntity<String> jsonResponse(Object value) throws JsonProcessingException {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(value));
    }
}
