package com.controlfinan.web;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.controlfinan.model.Repository;
import com.controlfinan.model.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class RegistrationsController {
    private final Web web;
    private final Repository repository;

    public RegistrationsController(Web web, Repository repository) {
        this.web = web;
        this.repository = repository;
    }

    @GetMapping("/registration/new")
    public String newRegistration(Model model, HttpServletRequest request, HttpServletResponse response) {
        model.addAttribute("emailAddress", "");
        return web.render("registrations/new", model, request, response);
    }

    @PostMapping("/registration")
    public String create(@RequestParam(name = "user[email_address]", defaultValue = "") String email,
                         @RequestParam(name = "user[password]", defaultValue = "") String password,
                         @RequestParam(name = "user[password_confirmation]", defaultValue = "") String confirmation,
                         Model model, HttpServletRequest request, HttpServletResponse response) {
        if (!web.allow("registrations:" + request.getRemoteAddr(), 10, Duration.ofMinutes(3))) {
            return web.redirect(response, "/registration/new", "alert", "Tente novamente mais tarde.");
        }
        User user = new User(0, email, null);
        List<String> errors = repository.validateUser(user, password, confirmation, true);
        if (!errors.isEmpty()) {
            response.setStatus(422);
            model.addAttribute("emailAddress", user.getEmailAddress());
            model.addAttribute("errors", errors);
            return web.render("registrations/new", model, request, response);
        }
        user.setPasswordDigest(web.hashPassword(password));
        repository.createUser(user);
        web.startNewSessionFor(request, response, user);
        return web.redirect(response, "/", "notice", "Conta criada.");
    }
}
