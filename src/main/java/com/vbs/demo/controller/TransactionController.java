package com.vbs.demo.controller;

import com.vbs.demo.dto.TransactionDto;
import com.vbs.demo.dto.TransferDto;
import com.vbs.demo.models.Transaction;
import com.vbs.demo.models.User;
import com.vbs.demo.repositories.TransactionRepo;
import com.vbs.demo.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class TransactionController {

    @Autowired UserRepo userRepo;
    @Autowired TransactionRepo transactionRepo;

    // ─── Fee policy ────────────────────────────────────────────────────────────
    // SAVINGS : No fees (reward for saving)
    // CURRENT : ₹10 flat fee per withdrawal, 0.5% fee on outbound transfers (min ₹5)
    private static final double CURRENT_WITHDRAW_FEE  = 10.0;
    private static final double CURRENT_TRANSFER_RATE = 0.005; // 0.5%
    private static final double CURRENT_TRANSFER_MIN  = 5.0;

    private double withdrawFee(User user) {
        return "CURRENT".equalsIgnoreCase(user.getAccountType()) ? CURRENT_WITHDRAW_FEE : 0;
    }

    private double transferFee(User user, double amount) {
        if (!"CURRENT".equalsIgnoreCase(user.getAccountType())) return 0;
        return round2(Math.max(CURRENT_TRANSFER_MIN, amount * CURRENT_TRANSFER_RATE));
    }

    // ─── Deposit ───────────────────────────────────────────────────────────────
    @PostMapping("/deposit")
    public String deposit(@RequestBody TransactionDto obj) {
        User user = userRepo.findById(obj.getId())
                .orElseThrow(() -> new RuntimeException("Not found"));

        if (obj.getAmount() <= 0) return "Invalid deposit amount";

        double newBalance = round2(user.getBalance() + obj.getAmount());
        user.setBalance(newBalance);
        userRepo.save(user);

        Transaction t = new Transaction();
        t.setAmount(obj.getAmount());
        t.setCurrBalance(newBalance);
        t.setDescription("Rs." + obj.getAmount() + " Deposit Successful");
        t.setUserId(obj.getId());
        transactionRepo.save(t);

        return "Deposit Successful";
    }

    // ─── Withdraw ──────────────────────────────────────────────────────────────
    @PostMapping("/withdraw")
    public String withdraw(@RequestBody TransactionDto obj) {
        User user = userRepo.findById(obj.getId())
                .orElseThrow(() -> new RuntimeException("Not found"));

        if (obj.getAmount() <= 0) return "Invalid withdrawal amount";

        double fee        = withdrawFee(user);
        double totalDebit = round2(obj.getAmount() + fee);

        if (user.getBalance() < totalDebit)
            return fee > 0
                    ? "Insufficient balance (amount + ₹" + fee + " service fee = ₹" + totalDebit + ")"
                    : "Insufficient balance";

        double newBalance = round2(user.getBalance() - totalDebit);
        user.setBalance(newBalance);
        userRepo.save(user);

        // Main withdrawal transaction
        Transaction t = new Transaction();
        t.setAmount(obj.getAmount());
        t.setCurrBalance(fee > 0 ? round2(newBalance + fee) : newBalance); // balance before fee deduction shown
        t.setDescription("Rs." + obj.getAmount() + " Withdrawal Successful"
                + (fee > 0 ? " | Service fee: Rs." + fee : ""));
        t.setUserId(obj.getId());
        transactionRepo.save(t);

        // Separate fee entry in passbook
        if (fee > 0) {
            Transaction feeT = new Transaction();
            feeT.setAmount(fee);
            feeT.setCurrBalance(newBalance);
            feeT.setDescription("Transaction fee for withdrawal (Current Account)");
            feeT.setUserId(obj.getId());
            transactionRepo.save(feeT);
        }

        return "Withdrawal Successful";
    }

    // ─── Transfer ──────────────────────────────────────────────────────────────
    @PostMapping("/transfer")
    public String transfer(@RequestBody TransferDto obj) {
        User sender = userRepo.findById(obj.getId())
                .orElseThrow(() -> new RuntimeException("Not found"));
        User rec = userRepo.findByUsername(obj.getUsername());

        if (rec == null)                      return "Username not found";
        if (obj.getAmount() < 1)             return "Invalid amount";
        if (sender.getId() == rec.getId())   return "Self transfer is not allowed";

        double fee        = transferFee(sender, obj.getAmount());
        double totalDebit = round2(obj.getAmount() + fee);

        if (sender.getBalance() < totalDebit)
            return fee > 0
                    ? "Insufficient balance (amount + ₹" + fee + " service fee = ₹" + totalDebit + ")"
                    : "Insufficient balance";

        double sBalance = round2(sender.getBalance() - totalDebit);
        double rBalance = round2(rec.getBalance() + obj.getAmount());

        sender.setBalance(sBalance);
        rec.setBalance(rBalance);
        userRepo.save(sender);
        userRepo.save(rec);

        // Sender debit
        Transaction t1 = new Transaction();
        t1.setAmount(obj.getAmount());
        t1.setCurrBalance(fee > 0 ? round2(sBalance + fee) : sBalance);
        t1.setDescription("Rs." + obj.getAmount() + " Sent to " + obj.getUsername()
                + (fee > 0 ? " | Service fee: Rs." + fee : ""));
        t1.setUserId(sender.getId());
        transactionRepo.save(t1);

        // Fee entry for sender
        if (fee > 0) {
            Transaction feeT = new Transaction();
            feeT.setAmount(fee);
            feeT.setCurrBalance(sBalance);
            feeT.setDescription("Transaction fee for transfer to " + obj.getUsername() + " (Current Account)");
            feeT.setUserId(sender.getId());
            transactionRepo.save(feeT);
        }

        // Receiver credit
        Transaction t2 = new Transaction();
        t2.setAmount(obj.getAmount());
        t2.setCurrBalance(rBalance);
        t2.setDescription("Rs." + obj.getAmount() + " Received from " + sender.getUsername());
        t2.setUserId(rec.getId());
        transactionRepo.save(t2);

        return "Transferred Successfully";
    }

    // ─── Passbook ──────────────────────────────────────────────────────────────
    @GetMapping("/passbook/{id}")
    public List<Transaction> getPassbook(@PathVariable int id) {
        return transactionRepo.findAllByUserId(id);
    }

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}