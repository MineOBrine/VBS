package com.vbs.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterDto {
    String name;
    String username;
    String email;
    String password;
    String role;
    String accountType;
    String inviteCode;  // Required only for admin signups
}