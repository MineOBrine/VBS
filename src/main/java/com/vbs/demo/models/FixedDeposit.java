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
public class FixedDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int id;

    @Column(nullable = false)
    int userId;

    @Column(nullable = false)
    double principal;

    /** Annual interest rate locked in at creation time (based on tenure tier) */
    @Column(nullable = false)
    double interestRate;

    /** Tenure in months (3–60) */
    @Column(nullable = false)
    int tenure;

    /** Simple interest: principal × rate × (tenure/12) / 100 */
    @Column(nullable = false)
    double interestEarned;

    @Column(nullable = false)
    double maturityAmount;   // principal + interestEarned

    /** ACTIVE | MATURED | BROKEN */
    @Column(nullable = false)
    String status;

    @Column(nullable = false)
    LocalDateTime startDate;

    @Column(nullable = false)
    LocalDateTime maturityDate;

    /** Whether to auto-renew on maturity */
    @Column(nullable = false)
    boolean autoRenew;

    @Column
    String notes;
}