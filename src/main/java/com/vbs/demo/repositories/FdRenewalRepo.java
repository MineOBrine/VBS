package com.vbs.demo.repositories;

import com.vbs.demo.models.FdRenewal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FdRenewalRepo extends JpaRepository<FdRenewal, Integer> {
    List<FdRenewal> findAllByFdIdOrderByRenewalNumberAsc(int fdId);
    List<FdRenewal> findAllByUserId(int userId);
    int countByFdId(int fdId);
}