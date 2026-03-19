package com.vbs.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id;

    @Column(nullable = false)
    int userId;

    @Column(nullable = false)
    double amount;           // principal requested

    @Column(nullable = false)
    double interestRate;     // annual % at time of application

    @Column(nullable = false)
    int tenure;              // months

    @Column(nullable = false)
    double emi;              // monthly EMI

    @Column(nullable = false)
    double totalAmount;      // total repayable (principal + interest)

    @Column(nullable = false)
    double remainingAmount;  // outstanding balance

    @Column(nullable = false)
    String status;           // PENDING | APPROVED | REJECTED | CLOSED

    @Column
    String purpose;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    LocalDateTime appliedDate;

    @Column
    LocalDateTime approvedDate;
}