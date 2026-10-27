package com.controlfinan.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Delivers the password reset e-mail. This one writes it to the log; tests replace it. */
@Component
public class Mailer {
    private static final Logger LOG = LoggerFactory.getLogger(Mailer.class);

    public void send(String to, String subject, String text) {
        LOG.info("mail to={} subject=\"{}\"\n{}", to, subject, text);
    }
}
