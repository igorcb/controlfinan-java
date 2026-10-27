package com.controlfinan.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.view.RedirectView;

/** 303 See Other redirects, which Rails uses after DELETE. */
final class Redirects {
    private Redirects() {
    }

    static RedirectView seeOther(String url) {
        RedirectView view = new RedirectView(url);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        view.setExposeModelAttributes(false);
        return view;
    }
}
