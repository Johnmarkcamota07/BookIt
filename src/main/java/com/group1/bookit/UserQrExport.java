/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Local utility until the registration/profile screen has a Generate QR button. */
public final class UserQrExport {
    private UserQrExport() {}

    public static void main(String[] args) throws Exception {
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Run from a terminal.");
        if (args.length != 1) {
            console.printf("Usage: UserQrExport output.png%n");
            return;
        }
        Path output = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(output) || !output.getFileName().toString().toLowerCase().endsWith(".png")) {
            throw new IllegalArgumentException("Choose a new PNG filename.");
        }
        if (!Files.isDirectory(output.getParent())) {
            throw new IllegalArgumentException("Output directory does not exist.");
        }
        String username = console.readLine("Username: ");
        char[] password = console.readPassword("Password: ");
        if (password == null) return;
        try {
            String token = new AuthenticationService().issueOrReplaceUserQr(
                    username, new String(password));
            if (token == null) {
                console.printf("Rejected: check credentials/account status. Admin QR login is disabled.%n");
                return;
            }
            // CREATE_NEW avoids overwriting a file even if another process created it.
            try (var stream = Files.newOutputStream(output,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE)) {
                if (!ImageIO.write(CodeGenerator.generateLoginQrCode(token), "PNG", stream)) {
                    throw new IllegalStateException("PNG image writer unavailable.");
                }
            }
            console.printf("Saved QR image: %s%nPrevious QR credential is now invalid.%n", output);
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}

