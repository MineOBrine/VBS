package com.vbs.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DisplayDto {
    String username;
    String name;
    String email;
    double balance;
    String accountType;   // SAVINGS | CURRENT | ADMIN
    String accountNumber; // VBS0000000001
    LocalDateTime createdAt;
}