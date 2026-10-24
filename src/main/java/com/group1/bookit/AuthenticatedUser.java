/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
//AuthenticatedUser(String userId, String username, String role) = This method helps securely hold the basic "ID card" details of someone who just successfully logged into the system.;
//How it works: It acts as an automatic, tamper-proof digital ID badge. Once the computer verifies a person's password, it takes their unique ID number, their username, and their permission level (like "ADMIN" or "USER") and stamps them permanently onto this digital badge. Because it is a special Java "record", the information on this badge is locked—it can never be altered, erased, or forged once it is created.
//Why is it used: It gives the application a fast and highly secure way to remember who is currently using the computer. Instead of constantly asking the main database "Who is this person and are they an admin?" every time a button is clicked, the system just checks this secure, unchangeable digital badge.
package com.group1.bookit;

public record AuthenticatedUser(String userId,String username,String role){
}