/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;

public final class LogoSupport {
    private LogoSupport() {}

    public static void install(ImageView view) {
        Image image = view.getImage();
        if (image == null) {
            var location = LogoSupport.class.getResource("images/logo2.png");
            if (location == null) throw new IllegalStateException("Missing images/logo2.png");
            image = new Image(location.toExternalForm());
            view.setImage(image);
        }
        if (image.isError()) throw new IllegalStateException("The BookIt logo could not be loaded.");
        PixelReader pixels = image.getPixelReader();
        int width = (int) image.getWidth(), height = (int) image.getHeight();
        if (pixels != null) {
            int left = width, top = height, right = -1, bottom = -1;
            for (int y = 0; y < height; y += 2) {
                for (int x = 0; x < width; x += 2) {
                    if ((pixels.getArgb(x, y) >>> 24) >= 40) {
                        left = Math.min(left, x); top = Math.min(top, y);
                        right = Math.max(right, x); bottom = Math.max(bottom, y);
                    }
                }
            }
            if (right >= left && bottom >= top) {
                left = Math.max(0, left - 12); top = Math.max(0, top - 12);
                right = Math.min(width - 1, right + 14); bottom = Math.min(height - 1, bottom + 14);
                view.setViewport(new Rectangle2D(left, top, right - left + 1, bottom - top + 1));
            }
        }
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setAccessibleText("BookIT library logo");
    }
}

