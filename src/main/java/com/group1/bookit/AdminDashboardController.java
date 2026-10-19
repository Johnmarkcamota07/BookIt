/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.group1.bookit.AuthenticationService.AuthenticatedUser;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class AdminDashboardController implements DashboardView {
    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;

    @Override
    public void setUser(AuthenticatedUser user) {
        if (user == null || !"ADMIN".equals(user.role())) {
            throw new IllegalArgumentException("The admin dashboard requires an ADMIN account.");
        }
        welcomeLabel.setText("Welcome, " + user.username() + ".");
        roleLabel.setText("Signed in as ADMIN");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        Navigation.logoutFrom(event);
    }

    // Add admin-specific button handlers here as their features are implemented.
}

