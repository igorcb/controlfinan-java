package com.controlfinan.web;

import java.time.Duration;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import com.controlfinan.model.Repository;
import com.controlfinan.model.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class SessionsController {
    private final Web web;
    private final Repository repository;

    public SessionsController(Web web, Repository repository) {
        this.web = web;
        this.repository = repository;
    }

    @GetMapping("/session/new")
    public String newSession(@RequestParam(name = "email_address", defaultValue = "") String email, Model model,
                             HttpServletRequest request, HttpServletResponse response) {
        model.addAttribute("emailAddress", email);
        return web.render("sessions/new", model, request, response);
    }

    @PostMapping("/session")
    public String create(@RequestParam(name = "email_address", defaultValue = "") String email,
                         @RequestParam(name = "password", defaultValue = "") String password,
                         HttpServletRequest request, HttpServletResponse response) {
        if (!web.allow("sessions:" + request.getRemoteAddr(), 10, Duration.ofMinutes(3))) {
            return web.redirect(response, "/session/new", "alert", "Tente novamente mais tarde.");
        }
        User user = repository.findUserByEmail(email);
        if (user == null) {
            web.checkDummyPassword();
        }
        if (user == null || !web.checkPassword(user, password)) {
            return web.redirect(response, "/session/new", "alert", "E-mail ou senha inválidos.");
        }
        web.startNewSessionFor(request, response, user);
        return "redirect:" + web.afterAuthenticationUrl(request, response);
    }

    @DeleteMapping("/session")
    public RedirectView destroy(HttpServletRequest request, HttpServletResponse response) {
        web.terminateSession(request, response);
        return Redirects.seeOther("/session/new");
    }
}
