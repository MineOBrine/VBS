package com.vbs.demo.service;

import com.vbs.demo.models.Transaction;
import com.vbs.demo.models.User;
import com.vbs.demo.repositories.TransactionRepo;
import com.vbs.demo.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterestScheduler {

    private static final double ANNUAL_SAVINGS_RATE = 4.0;  // 4% p.a.
    private static final double MONTHLY_RATE = ANNUAL_SAVINGS_RATE / 12.0 / 100.0;

    @Autowired UserRepo userRepo;
    @Autowired TransactionRepo transactionRepo;

    /**
     * Auto-runs on the 1st of every month at midnight.
     * Credits monthly interest to all SAVINGS accounts with balance > 0.
     * Also exposed as a public method so admins can trigger it manually.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public String applyMonthlyInterest() {
        List<User> savingsUsers = userRepo.findAllByAccountType("SAVINGS");
        int count = 0;

        for (User user : savingsUsers) {
            if (user.getBalance() > 0) {
                double interest = Math.round(user.getBalance() * MONTHLY_RATE * 100.0) / 100.0;
                double newBalance = user.getBalance() + interest;
                user.setBalance(newBalance);
                userRepo.save(user);

                Transaction t = new Transaction();
                t.setAmount(interest);
                t.setCurrBalance(newBalance);
                t.setDescription("Monthly savings interest @ " + ANNUAL_SAVINGS_RATE + "% p.a. credited");
                t.setUserId(user.getId());
                transactionRepo.save(t);
                count++;
            }
        }

        String msg = "Monthly interest applied to " + count + " savings account(s).";
        System.out.println(msg);
        return msg;
    }
}