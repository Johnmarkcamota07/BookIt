/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import java.util.Objects;

public final class SessionManager {

    private static AuthenticatedUser currentUser;

    private SessionManager() {
    }

    public static void login(AuthenticatedUser user) {
        currentUser = Objects.requireNonNull(user);
    }

    public static AuthenticatedUser getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static void logout() {
        currentUser = null;
    }
}
