/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

public record ScannedBookCopy(String copyId, String bookId, String title, String barcode,
                              String status, String shelf, String openLoanId, String memberName) {}

