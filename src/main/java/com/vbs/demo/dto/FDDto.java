package com.vbs.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FDDto {
    int userId;
    double principal;
    int tenure;        // months
    boolean autoRenew;
    String notes;
}