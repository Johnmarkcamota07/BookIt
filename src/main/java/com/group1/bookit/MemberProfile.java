/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;
import java.time.LocalDateTime;
/**
 *
 * @author John Mark
 */
/** A member's non-secret account details. Database timestamps are UTC. */
public record MemberProfile(String userId, String username, String fullName,
        String email, String phone, String role, String status,
        LocalDateTime joinedAt, LocalDateTime updatedAt, LocalDateTime lastLoginAt) {
}