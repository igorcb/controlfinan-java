package com.controlfinan.web;

import java.io.IOException;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs before the "_method" filter: checks the CSRF token of every POST, loads the session and
 * sends guests to the login (Rails' require_authentication), except on the public pages.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC = Set.of("/session/new", "/session", "/registration/new", "/registration", "/passwords/new",
        "/passwords", "/up", "/search_account", "/error", "/icon.png", "/icon.svg");

    private final Web web;

    public RequestFilter(Web web) {
        this.web = web;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod()) && !Web.validCsrf(request)) {
            response.sendError(422);
            return;
        }
        web.resumeSession(request);
        if (Web.currentUser(request) == null && requiresAuthentication(request, path)) {
            String query = request.getQueryString();
            web.setSigned(response, Web.RETURN_TO_COOKIE, Web.absoluteUrl(request, path + (query == null ? "" : "?" + query)), 0);
            response.sendRedirect("/session/new");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean requiresAuthentication(HttpServletRequest request, String path) {
        if (path.equals("/session") && "delete".equalsIgnoreCase(request.getParameter("_method"))) {
            return true; // signing out needs a session
        }
        return !(PUBLIC.contains(path) || path.startsWith("/passwords/") || path.startsWith("/css/") || path.startsWith("/js/"));
    }
}
