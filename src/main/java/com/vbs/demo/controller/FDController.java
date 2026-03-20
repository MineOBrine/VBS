package com.vbs.demo.controller;

import com.vbs.demo.dto.FDDto;
import com.vbs.demo.models.FdRenewal;
import com.vbs.demo.models.FixedDeposit;
import com.vbs.demo.models.Transaction;
import com.vbs.demo.models.User;
import com.vbs.demo.repositories.FDRepo;
import com.vbs.demo.repositories.FdRenewalRepo;
import com.vbs.demo.repositories.TransactionRepo;
import com.vbs.demo.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class FDController {

    @Autowired FDRepo fdRepo;
    @Autowired FdRenewalRepo renewalRepo;
    @Autowired UserRepo userRepo;
    @Autowired TransactionRepo transactionRepo;

    // ── Interest rate tiers ───────────────────────────────────────────────
    private double rateForTenure(int months) {
        if (months <= 6)  return 5.5;
        if (months <= 12) return 6.5;
        if (months <= 24) return 7.0;
        return 7.5;
    }

    // ── Create FD ─────────────────────────────────────────────────────────
    @PostMapping("/fd/create")
    public String createFD(@RequestBody FDDto dto) {
        if (dto.getPrincipal() < 500)           return "Minimum FD amount is Rs.500";
        if (dto.getTenure() < 3 || dto.getTenure() > 60) return "FD tenure must be between 3 and 60 months";

        User user = userRepo.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getBalance() < dto.getPrincipal()) return "Insufficient balance to open Fixed Deposit";

        double rate     = rateForTenure(dto.getTenure());
        double interest = round2(dto.getPrincipal() * rate * dto.getTenure() / (12.0 * 100.0));
        double maturity = round2(dto.getPrincipal() + interest);

        double newBalance = round2(user.getBalance() - dto.getPrincipal());
        user.setBalance(newBalance);
        userRepo.save(user);

        Transaction t = new Transaction();
        t.setAmount(dto.getPrincipal());
        t.setCurrBalance(newBalance);
        t.setDescription("Fixed Deposit #opened - Rs." + dto.getPrincipal()
                + " locked for " + dto.getTenure() + " months @ " + rate + "% p.a.");
        t.setUserId(user.getId());
        transactionRepo.save(t);

        FixedDeposit fd = new FixedDeposit();
        fd.setUserId(dto.getUserId());
        fd.setPrincipal(dto.getPrincipal());
        fd.setInterestRate(rate);
        fd.setTenure(dto.getTenure());
        fd.setInterestEarned(interest);
        fd.setMaturityAmount(maturity);
        fd.setStatus("ACTIVE");
        fd.setStartDate(LocalDateTime.now());
        fd.setMaturityDate(LocalDateTime.now().plusMonths(dto.getTenure()));
        fd.setAutoRenew(dto.isAutoRenew());
        fd.setNotes(dto.getNotes() != null ? dto.getNotes() : "");
        fdRepo.save(fd);

        return "FD created successfully. Rs." + dto.getPrincipal()
                + " locked. Maturity amount: Rs." + maturity + " after " + dto.getTenure() + " months.";
    }

    // ── Get user FDs ──────────────────────────────────────────────────────
    @GetMapping("/fd/user/{userId}")
    public List<FixedDeposit> getUserFDs(@PathVariable int userId) {
        return fdRepo.findAllByUserId(userId);
    }

    // ── Get renewal history for a specific FD ─────────────────────────────
    @GetMapping("/fd/renewals/{fdId}")
    public List<FdRenewal> getRenewals(@PathVariable int fdId) {
        return renewalRepo.findAllByFdIdOrderByRenewalNumberAsc(fdId);
    }

    // ── Admin: all FDs ────────────────────────────────────────────────────
    @GetMapping("/fd/all")
    public List<FixedDeposit> getAllFDs() {
        return fdRepo.findAll();
    }

    // ── Break FD (premature withdrawal) ──────────────────────────────────
    @PostMapping("/fd/break/{fdId}")
    public String breakFD(@PathVariable int fdId, @RequestParam int userId) {
        FixedDeposit fd = fdRepo.findById(fdId)
                .orElseThrow(() -> new RuntimeException("FD not found"));
        if (fd.getUserId() != userId)       return "Unauthorized";
        if (!fd.getStatus().equals("ACTIVE")) return "This FD is not active";

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        double penalizedRate  = Math.max(0, fd.getInterestRate() - 1.0);
        long   daysElapsed    = java.time.temporal.ChronoUnit.DAYS.between(fd.getStartDate(), LocalDateTime.now());
        double monthsElapsed  = daysElapsed / 30.4;
        double earnedInterest = round2(fd.getPrincipal() * penalizedRate * monthsElapsed / (12.0 * 100.0));
        double payout         = round2(fd.getPrincipal() + earnedInterest);

        double newBalance = round2(user.getBalance() + payout);
        user.setBalance(newBalance);
        userRepo.save(user);

        fd.setStatus("BROKEN");
        fd.setInterestEarned(earnedInterest);
        fd.setMaturityAmount(payout);
        fdRepo.save(fd);

        Transaction t = new Transaction();
        t.setAmount(payout);
        t.setCurrBalance(newBalance);
        t.setDescription("Fixed Deposit #" + fd.getId() + " broken early - Rs." + payout
                + " credited (penalized rate: " + penalizedRate + "% p.a.)");
        t.setUserId(userId);
        transactionRepo.save(t);

        return "FD broken. Rs." + payout + " credited to your account (penalized rate: " + penalizedRate + "% p.a.)";
    }

    // ── Scheduled: process matured FDs daily at midnight ──────────────────
    @Scheduled(cron = "0 0 0 * * *")
    public void processMaturities() {
        List<FixedDeposit> matured =
                fdRepo.findAllByStatusAndMaturityDateBefore("ACTIVE", LocalDateTime.now());

        for (FixedDeposit fd : matured) {
            User user = userRepo.findById(fd.getUserId()).orElse(null);
            if (user == null) continue;

            if (fd.isAutoRenew()) {
                // ── Save this renewal into history ──────────────────────
                int renewalCount = renewalRepo.countByFdId(fd.getId()) + 1;

                FdRenewal renewal = new FdRenewal();
                renewal.setFdId(fd.getId());
                renewal.setUserId(fd.getUserId());
                renewal.setRenewalNumber(renewalCount);
                renewal.setPrincipal(fd.getPrincipal());
                renewal.setInterestRate(fd.getInterestRate());
                renewal.setTenure(fd.getTenure());
                renewal.setInterestEarned(fd.getInterestEarned());
                renewal.setMaturityAmount(fd.getMaturityAmount());
                renewal.setStartDate(fd.getStartDate());
                renewal.setMaturityDate(fd.getMaturityDate());
                renewalRepo.save(renewal);

                // ── Reset FD for next cycle ─────────────────────────────
                double newRate     = rateForTenure(fd.getTenure());
                double newInterest = round2(fd.getPrincipal() * newRate * fd.getTenure() / (12.0 * 100.0));
                fd.setInterestRate(newRate);
                fd.setInterestEarned(newInterest);
                fd.setMaturityAmount(round2(fd.getPrincipal() + newInterest));
                fd.setStartDate(LocalDateTime.now());
                fd.setMaturityDate(LocalDateTime.now().plusMonths(fd.getTenure()));
                fdRepo.save(fd);

                Transaction t = new Transaction();
                t.setAmount(0);
                t.setCurrBalance(user.getBalance());
                t.setDescription("Fixed Deposit #" + fd.getId() + " auto-renewed (renewal #" + renewalCount
                        + ") for " + fd.getTenure() + " months @ " + newRate + "% p.a.");
                t.setUserId(user.getId());
                transactionRepo.save(t);

            } else {
                // ── Credit maturity amount ──────────────────────────────
                double newBalance = round2(user.getBalance() + fd.getMaturityAmount());
                user.setBalance(newBalance);
                userRepo.save(user);

                fd.setStatus("MATURED");
                fdRepo.save(fd);

                Transaction t = new Transaction();
                t.setAmount(fd.getMaturityAmount());
                t.setCurrBalance(newBalance);
                t.setDescription("Fixed Deposit #" + fd.getId() + " matured - Rs." + fd.getMaturityAmount()
                        + " credited (Principal: Rs." + fd.getPrincipal()
                        + " + Interest: Rs." + fd.getInterestEarned() + ")");
                t.setUserId(user.getId());
                transactionRepo.save(t);
            }
        }
        System.out.println("FD maturity check: processed " + matured.size() + " FD(s).");
    }

    // ── Preview ────────────────────────────────────────────────────────────
    @GetMapping("/fd/preview")
    public String previewFD(@RequestParam double principal, @RequestParam int tenure) {
        if (tenure < 3 || tenure > 60) return "Invalid tenure";
        double rate     = rateForTenure(tenure);
        double interest = round2(principal * rate * tenure / (12.0 * 100.0));
        double maturity = round2(principal + interest);
        return String.format("{\"rate\":%.1f,\"interest\":%.2f,\"maturity\":%.2f}", rate, interest, maturity);
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}