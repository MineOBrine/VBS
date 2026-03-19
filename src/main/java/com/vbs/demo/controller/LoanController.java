package com.vbs.demo.controller;

import com.vbs.demo.dto.LoanDto;
import com.vbs.demo.dto.LoanRepayDto;
import com.vbs.demo.models.History;
import com.vbs.demo.models.Loan;
import com.vbs.demo.models.Transaction;
import com.vbs.demo.models.User;
import com.vbs.demo.repositories.HistoryRepo;
import com.vbs.demo.repositories.LoanRepo;
import com.vbs.demo.repositories.TransactionRepo;
import com.vbs.demo.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class LoanController {

    private static final double LOAN_INTEREST_RATE = 10.0; // 10% p.a.

    @Autowired LoanRepo loanRepo;
    @Autowired UserRepo userRepo;
    @Autowired TransactionRepo transactionRepo;
    @Autowired HistoryRepo historyRepo;

    // ─── Customer: apply for a loan ───────────────────────────────────────────

    @PostMapping("/loan/apply")
    public String applyLoan(@RequestBody LoanDto dto) {
        userRepo.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (dto.getAmount() < 1000)
            return "Minimum loan amount is Rs.1000";
        if (dto.getTenure() < 1 || dto.getTenure() > 60)
            return "Tenure must be between 1 and 60 months";

        boolean hasActive = loanRepo.findAllByUserId(dto.getUserId()).stream()
                .anyMatch(l -> l.getStatus().equals("APPROVED") || l.getStatus().equals("PENDING"));
        if (hasActive)
            return "You already have an active or pending loan. Please close it first.";

        double r = LOAN_INTEREST_RATE / 12.0 / 100.0;
        double emi = (dto.getAmount() * r * Math.pow(1 + r, dto.getTenure()))
                / (Math.pow(1 + r, dto.getTenure()) - 1);
        double totalAmount = emi * dto.getTenure();

        Loan loan = new Loan();
        loan.setUserId(dto.getUserId());
        loan.setAmount(dto.getAmount());
        loan.setInterestRate(LOAN_INTEREST_RATE);
        loan.setTenure(dto.getTenure());
        loan.setEmi(round2(emi));
        loan.setTotalAmount(round2(totalAmount));
        loan.setRemainingAmount(round2(totalAmount));
        loan.setStatus("PENDING");
        loan.setPurpose(dto.getPurpose() != null ? dto.getPurpose() : "");

        loanRepo.save(loan);
        return "Loan application submitted successfully";
    }

    // ─── Customer: view own loans ──────────────────────────────────────────────

    @GetMapping("/loan/user/{userId}")
    public List<Loan> getUserLoans(@PathVariable int userId) {
        return loanRepo.findAllByUserId(userId);
    }

    // ─── Admin: view all loans ─────────────────────────────────────────────────

    @GetMapping("/loan/all")
    public List<Loan> getAllLoans() {
        return loanRepo.findAll();
    }

    // ─── Admin: approve loan ───────────────────────────────────────────────────

    @PostMapping("/loan/approve/{loanId}/admin/{adminId}")
    public String approveLoan(@PathVariable int loanId, @PathVariable int adminId) {
        Loan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!loan.getStatus().equals("PENDING"))
            return "Loan is not in PENDING state";

        User user = userRepo.findById(loan.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        loan.setStatus("APPROVED");
        loan.setApprovedDate(LocalDateTime.now());
        loanRepo.save(loan);

        double newBalance = user.getBalance() + loan.getAmount();
        user.setBalance(newBalance);
        userRepo.save(user);

        Transaction t = new Transaction();
        t.setAmount(loan.getAmount());
        t.setCurrBalance(newBalance);
        t.setDescription("Loan #" + loan.getId() + " of Rs." + loan.getAmount() + " credited to account");
        t.setUserId(user.getId());
        transactionRepo.save(t);

        History h = new History();
        h.setDescription("Admin " + adminId + " approved loan #" + loanId
                + " (Rs." + loan.getAmount() + ") for user " + user.getUsername());
        historyRepo.save(h);

        return "Loan approved. Rs." + loan.getAmount() + " credited to user account.";
    }

    // ─── Admin: reject loan ────────────────────────────────────────────────────

    @PostMapping("/loan/reject/{loanId}/admin/{adminId}")
    public String rejectLoan(@PathVariable int loanId, @PathVariable int adminId) {
        Loan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!loan.getStatus().equals("PENDING"))
            return "Loan is not in PENDING state";

        User user = userRepo.findById(loan.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        loan.setStatus("REJECTED");
        loanRepo.save(loan);

        History h = new History();
        h.setDescription("Admin " + adminId + " rejected loan #" + loanId
                + " for user " + user.getUsername());
        historyRepo.save(h);

        return "Loan rejected successfully";
    }

    // ─── Customer: repay loan (full or partial) ────────────────────────────────

    @PostMapping("/loan/repay/{loanId}")
    public String repayLoan(@PathVariable int loanId, @RequestBody LoanRepayDto dto) {
        Loan loan = loanRepo.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if (!loan.getStatus().equals("APPROVED"))
            return "This loan is not currently active";

        User user = userRepo.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (dto.getAmount() <= 0)
            return "Invalid repayment amount";
        if (user.getBalance() < dto.getAmount())
            return "Insufficient balance for repayment";

        // Cap repayment at remaining amount
        double repayAmt = round2(Math.min(dto.getAmount(), loan.getRemainingAmount()));
        double newBalance = user.getBalance() - repayAmt;
        user.setBalance(newBalance);
        userRepo.save(user);

        double newRemaining = round2(loan.getRemainingAmount() - repayAmt);
        loan.setRemainingAmount(newRemaining);
        if (newRemaining <= 0.01) {
            loan.setStatus("CLOSED");
            loan.setRemainingAmount(0);
        }
        loanRepo.save(loan);

        Transaction t = new Transaction();
        t.setAmount(repayAmt);
        t.setCurrBalance(newBalance);
        t.setDescription("Loan #" + loan.getId() + " repayment of Rs." + repayAmt);
        t.setUserId(user.getId());
        transactionRepo.save(t);

        if (loan.getStatus().equals("CLOSED"))
            return "Loan fully repaid and closed!";

        return "Repayment of Rs." + repayAmt + " successful. Remaining: Rs." + loan.getRemainingAmount();
    }

    // ─── Utility ───────────────────────────────────────────────────────────────

    private double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}