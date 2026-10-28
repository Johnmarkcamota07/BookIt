/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import java.time.LocalDateTime;

public record LoanRenewal(String loanId, String requestId, String bookTitle,
        String barcode, LocalDateTime previousDueAt, LocalDateTime newDueAt,
        LocalDateTime renewedAt) {}

