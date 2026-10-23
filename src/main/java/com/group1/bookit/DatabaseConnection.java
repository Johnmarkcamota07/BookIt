/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;

/** One explicitly selected database; connection failures never become null. */
public final class DatabaseConnection {
    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        final Dotenv dotenv;
        try {
            dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (RuntimeException error) {
            // A dotenv parse exception can contain the bad line, including secrets.
            throw new SQLException("Database configuration could not be loaded. Check the .env file.", "08001");
        }

        String mode = value(dotenv, "DB_MODE");
        mode = mode == null ? "ONLINE" : mode.strip().toUpperCase(Locale.ROOT);
        if (!mode.equals("ONLINE") && !mode.equals("LOCAL")) {
            throw new SQLException("DB_MODE must be ONLINE or LOCAL.", "08001");
        }
        String prefix = mode.equals("LOCAL") ? "LOCAL_DB_" : "DB_";
        String url = required(dotenv, prefix + "URL").strip();
        String user = required(dotenv, prefix + "USER").strip();
        // Keep DB_PASS from the user's existing configuration. Do not trim passwords.
        String password = value(dotenv, prefix + "PASS");
        if (password == null) {
            throw new SQLException("Missing database configuration: " + prefix + "PASS", "08001");
        }
        if (!url.startsWith("jdbc:mysql:")) {
            throw new SQLException("The selected database URL must start with jdbc:mysql:.", "08001");
        }

        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        // Connector/J defaults for URLs that do not override these properties.
        properties.setProperty("connectTimeout", "10000");
        properties.setProperty("socketTimeout", "15000");

        Connection connection = DriverManager.getConnection(url, properties);
        try (Statement statement = connection.createStatement()) {
            // Existing schema/authentication use UTC DATETIME values.
            statement.setQueryTimeout(12);
            statement.execute("SET time_zone = '+00:00'");
        } catch (SQLException error) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
        return connection;
    }

    private static String required(Dotenv dotenv, String key) throws SQLException {
        String value = value(dotenv, key);
        if (value == null || value.isBlank()) {
            throw new SQLException("Missing database configuration: " + key, "08001");
        }
        return value;
    }

    private static String value(Dotenv dotenv, String key) {
        String environment = System.getenv(key);
        return environment != null ? environment : dotenv.get(key);
    }
}
