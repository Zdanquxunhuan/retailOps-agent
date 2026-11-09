package com.yu.retailops.support;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Arrays;

/** Local operator setup tool. Does not start Spring or connect to any external service. */
public final class PasswordHashTool {
    private PasswordHashTool() { }
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("Run from an interactive terminal; do not pass passwords as arguments");
        char[] password = console.readPassword("Operator password (minimum 12 characters): ");
        if (password == null) return;
        try {
            if (password.length < 12) throw new IllegalArgumentException("Password must contain at least 12 characters");
            console.printf("%s%n", "{bcrypt}" + new BCryptPasswordEncoder(12).encode(new String(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
