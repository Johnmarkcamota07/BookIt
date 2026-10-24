/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
//
//AccountSetup() = This method helps lock this specific tool so no one can make accidental copies of it.;
//How it works: It acts like a lock on a blueprint, blocking the computer system from trying to build or duplicate this specific piece of the program.
//Why is it used: It keeps the system clean and organized. Since this tool is just a one-time setup helper, there is no need for the system to ever create multiple copies of it running around in the background.
//
//main(String[] args) = This method helps create a brand new user or admin account securely from behind the scenes.;
//How it works: First, it forces you to use a secure black text screen (a terminal) so nobody looking over your shoulder can see the password you are typing. It asks for your username, email, and name, checking to make sure you didn't leave anything blank or make it too short. After you type and confirm your password, it scrambles that password into a secret, unreadable code so even the computer doesn't know your real password. Finally, it saves all this information into the system's digital filing cabinet (the database) and instantly erases your real password from its short-term memory.
//Why is it used: It is used as a secure, private way for the system owners to make the very first accounts. Because this specific app doesn't have a public "Sign Up Here" button for just anyone to use, the creators need this safe, manual way to add trusted staff or admins into the system.
package com.group1.bookit;

import java.io.Console;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;

/** FOR ADMIN ACCOUNT CREATION ONLY: DI PWEDE MAKITA NG USER TO */
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

