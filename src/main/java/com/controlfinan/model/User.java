package com.controlfinan.model;

/** Mirrors the Rails users table (has_secure_password). */
public class User {
    private long id;
    private String emailAddress;
    private String passwordDigest;

    public User() {
    }

    public User(long id, String emailAddress, String passwordDigest) {
        this.id = id;
        this.emailAddress = emailAddress;
        this.passwordDigest = passwordDigest;
    }

    public static String normalizeEmail(String value) {
        return value == null ? "" : value.strip().toLowerCase();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getPasswordDigest() {
        return passwordDigest;
    }

    public void setPasswordDigest(String passwordDigest) {
        this.passwordDigest = passwordDigest;
    }
}
