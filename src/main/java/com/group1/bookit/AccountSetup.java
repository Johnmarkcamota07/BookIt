/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.io.Console;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;

/** One-time LOCAL developer/admin setup utility, not a public registration screen. */
public final class AccountSetup {
    private AccountSetup() {}

    public static void main(String[] args) throws Exception {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Run from a terminal so passwords can be hidden.");
        }
        if (args.length != 1 || !(args[0].equals("ADMIN") || args[0].equals("USER"))) {
            console.printf("Usage: AccountSetup ADMIN|USER%n");
            return;
        }
        String username = console.readLine("Username: ");
        String email = console.readLine("Email: ");
        String name = console.readLine("Full name: ");
        if (username == null || !username.matches("[A-Za-z0-9._-]{3,64}")
                || email == null || email.isBlank() || email.length() > 255
                || name == null || name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException("Invalid username, email, or name.");
        }
        char[] password = console.readPassword("Password (8+ characters): ");
        char[] confirmation = console.readPassword("Confirm password: ");
        try {
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Passwords do not match.");
            }
            String hash = PasswordHasher.hash(password);
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO app_user (username, email, full_name, password_hash, role)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
                statement.setString(1, username);
                statement.setString(2, email.trim());
                statement.setString(3, name.trim());
                statement.setString(4, hash);
                statement.setString(5, args[0]);
                statement.executeUpdate();
            }
            console.printf("Account created. You can now log in.%n");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}

