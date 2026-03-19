package com.vbs.demo.repositories;

import com.vbs.demo.models.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanRepo extends JpaRepository<Loan, Integer> {
    List<Loan> findAllByUserId(int userId);
    List<Loan> findAllByStatus(String status);
    List<Loan> findAllByUserIdAndStatus(int userId, String status);
}