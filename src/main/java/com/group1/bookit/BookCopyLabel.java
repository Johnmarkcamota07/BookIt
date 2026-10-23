/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

/**
 *
 * @author John Mark
 */

/** Label-safe physical-copy data. This value deliberately has no borrower fields. */
public record BookCopyLabel(String copyId, String bookId, String title, String barcode, String shelf) {}
