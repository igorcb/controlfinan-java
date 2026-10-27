package com.controlfinan.web;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.controlfinan.model.Repository;
import com.controlfinan.model.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class PasswordsController {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordsController.class);
    private static final String INVALID = "O link de redefinição é inválido ou expirou.";

    private final Web web;
    private final Repository repository;
    private final Mailer mailer;

    public PasswordsController(Web web, Repository repository, Mailer mailer) {
        this.web = web;
        this.repository = repository;
        this.mailer = mailer;
    }

    @GetMapping("/passwords/new")
    public String newPassword(@RequestParam(name = "email_address", defaultValue = "") String email, Model model,
                              HttpServletRequest request, HttpServletResponse response) {
        model.addAttribute("emailAddress", email);
        return web.render("passwords/new", model, request, response);
    }

    @PostMapping("/passwords")
    public String create(@RequestParam(name = "email_address", defaultValue = "") String email,
                         HttpServletRequest request, HttpServletResponse response) {
        if (!web.allow("passwords:" + request.getRemoteAddr(), 10, Duration.ofMinutes(3))) {
            return web.redirect(response, "/passwords/new", "alert", "Tente novamente mais tarde.");
        }
        User user = repository.findUserByEmail(email);
        if (user != null) {
            String url = Web.absoluteUrl(request, "/passwords/" + web.passwordResetToken(user) + "/edit");
            try {
                mailer.send(user.getEmailAddress(), "Redefinição de senha",
                    "Você pode redefinir sua senha em\n" + url + "\n\nEste link expira em 15 minutos.\n");
            } catch (RuntimeException e) {
                LOG.error("mail error", e);
            }
        }
        return web.redirect(response, "/session/new", "notice", "Se o e-mail estiver cadastrado, enviamos as instruções para redefinir a senha.");
    }

    @GetMapping("/passwords/{token}/edit")
    public String edit(@PathVariable String token, Model model, HttpServletRequest request, HttpServletResponse response) {
        if (web.findByPasswordResetToken(token) == null) {
            return web.redirect(response, "/passwords/new", "alert", INVALID);
        }
        model.addAttribute("token", token);
        return web.render("passwords/edit", model, request, response);
    }

    @RequestMapping(value = "/passwords/{token}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public String update(@PathVariable String token, @RequestParam(defaultValue = "") String password,
                         @RequestParam(name = "password_confirmation", defaultValue = "") String confirmation, HttpServletResponse response) {
        User user = web.findByPasswordResetToken(token);
        if (user == null) {
            return web.redirect(response, "/passwords/new", "alert", INVALID);
        }
        if (!repository.validateUser(user, password, confirmation, true).isEmpty()) {
            return web.redirect(response, "/passwords/" + token + "/edit", "alert", "As senhas não conferem ou têm menos de 6 caracteres.");
        }
        user.setPasswordDigest(web.hashPassword(password));
        repository.updatePassword(user);
        repository.deleteSessionsOf(user.getId());
        return web.redirect(response, "/session/new", "notice", "Senha redefinida.");
    }
}
