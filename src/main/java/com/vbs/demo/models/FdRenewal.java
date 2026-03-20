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
public class FdRenewal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id;

    @Column(nullable = false)
    int fdId;              // reference to the parent FixedDeposit

    @Column(nullable = false)
    int userId;

    @Column(nullable = false)
    int renewalNumber;     // 1st renewal, 2nd renewal, etc.

    @Column(nullable = false)
    double principal;

    @Column(nullable = false)
    double interestRate;

    @Column(nullable = false)
    int tenure;

    @Column(nullable = false)
    double interestEarned;

    @Column(nullable = false)
    double maturityAmount;

    @Column(nullable = false)
    LocalDateTime startDate;

    @Column(nullable = false)
    LocalDateTime maturityDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    LocalDateTime renewedAt;
}