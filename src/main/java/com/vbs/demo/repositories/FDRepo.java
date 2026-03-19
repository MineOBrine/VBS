package com.vbs.demo.repositories;

import com.vbs.demo.models.FixedDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FDRepo extends JpaRepository<FixedDeposit, Integer> {
    List<FixedDeposit> findAllByUserId(int userId);
    List<FixedDeposit> findAllByStatus(String status);
    /** Used by maturity scheduler – all active FDs whose maturity date has passed */
    List<FixedDeposit> findAllByStatusAndMaturityDateBefore(String status, LocalDateTime now);
}