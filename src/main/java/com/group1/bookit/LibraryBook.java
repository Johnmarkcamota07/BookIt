/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

public record LibraryBook(
        String bookId,
        String title,
        String authors,
        String category,
        String isbn,
        int totalCopies,
        int availableCopies) {
}
