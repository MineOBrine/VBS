package com.vbs.demo.controller;

import com.vbs.demo.dto.DisplayDto;
import com.vbs.demo.dto.LoginDto;
import com.vbs.demo.dto.UpdateDto;
import com.vbs.demo.models.History;
import com.vbs.demo.models.Transaction;
import com.vbs.demo.models.User;
import com.vbs.demo.repositories.HistoryRepo;
import com.vbs.demo.repositories.TransactionRepo;
import com.vbs.demo.repositories.UserRepo;
import com.vbs.demo.service.InterestScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired UserRepo userRepo;
    @Autowired HistoryRepo historyRepo;
    @Autowired TransactionRepo transactionRepo;
    @Autowired InterestScheduler interestScheduler;

    // ── Change this to whatever secret you want ────────────────────────────────
    private static final String ADMIN_INVITE_CODE = "VBS@Admin2025";

    @PostMapping("/register")
    public String register(@RequestBody com.vbs.demo.dto.RegisterDto dto) {
        // Validate invite code for admin signups
        if ("admin".equalsIgnoreCase(dto.getRole())) {
            if (dto.getInviteCode() == null || !dto.getInviteCode().equals(ADMIN_INVITE_CODE)) {
                return "Invalid invite code. Admin accounts require a valid invite code.";
            }
        }

        User user = new User();
        user.setName(dto.getName());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword());
        user.setRole(dto.getRole());
        user.setBalance(0);

        if ("admin".equalsIgnoreCase(dto.getRole())) {
            user.setAccountType("ADMIN");
        } else {
            String accType = dto.getAccountType();
            user.setAccountType(accType != null && !accType.isBlank() ? accType : "SAVINGS");
        }

        userRepo.save(user);
        return "Successfully Registered";
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginDto u) {
        User user = userRepo.findByUsername(u.getUsername());
        if (user == null)                                 return "User not found";
        if (!user.getPassword().equals(u.getPassword())) return "Password Incorrect";
        if (!user.getRole().equals(u.getRole()))          return "Role Incorrect";
        return String.valueOf(user.getId());
    }

    @GetMapping("/get-details/{id}")
    public DisplayDto display(@PathVariable int id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        DisplayDto dto = new DisplayDto();
        dto.setUsername(user.getUsername());
        dto.setBalance(user.getBalance());
        dto.setAccountType(user.getAccountType() != null ? user.getAccountType() : "SAVINGS");
        return dto;
    }

    @PostMapping("/update")
    public String update(@RequestBody UpdateDto obj) {
        User user = userRepo.findById(obj.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        switch (obj.getKey().toLowerCase()) {
            case "name":
                if (obj.getValue().equals(user.getName())) return "Cannot be same";
                user.setName(obj.getValue()); break;
            case "password":
                if (obj.getValue().equals(user.getPassword())) return "Cannot be same";
                user.setPassword(obj.getValue()); break;
            case "email":
                if (obj.getValue().equals(user.getEmail())) return "Cannot be same";
                if (userRepo.findByEmail(obj.getValue()) != null) return "Email Already Exists";
                user.setEmail(obj.getValue()); break;
            default: return "Invalid Key";
        }
        userRepo.save(user);
        return "Updated Successfully";
    }

    @PostMapping("/add/{adminId}")
    public String add(@RequestBody User user, @PathVariable int adminId) {
        if ("admin".equalsIgnoreCase(user.getRole())) {
            user.setAccountType("ADMIN");
        } else if (user.getAccountType() == null || user.getAccountType().isBlank()) {
            user.setAccountType("SAVINGS");
        }
        userRepo.save(user);
        if (user.getBalance() > 0) {
            User saved = userRepo.findByUsername(user.getUsername());
            Transaction t = new Transaction();
            t.setAmount(user.getBalance());
            t.setCurrBalance(user.getBalance());
            t.setDescription("Rs." + user.getBalance() + " initial deposit credited");
            t.setUserId(saved.getId());
            transactionRepo.save(t);
        }
        History h = new History();
        h.setDescription("Admin " + adminId + " created user " + user.getUsername()
                + " (" + user.getAccountType() + " account)");
        historyRepo.save(h);
        return "Added Successfully";
    }

    @DeleteMapping("/delete-user/{userId}/admin/{adminId}")
    public String delete(@PathVariable int userId, @PathVariable int adminId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getBalance() > 0) return "Please empty account to delete";
        History h = new History();
        h.setDescription("Admin " + adminId + " deleted user " + user.getUsername());
        historyRepo.save(h);
        userRepo.delete(user);
        return "User Deleted Successfully";
    }

    @GetMapping("/users")
    public List<User> getAllUsers(@RequestParam String sortBy, @RequestParam String order) {
        Sort sort = order.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        return userRepo.findAllByRole("customer", sort);
    }

    @GetMapping("/users/{keyword}")
    public List<User> searchUsers(@PathVariable String keyword) {
        return userRepo.findByUsernameContainingIgnoreCaseAndRole(keyword, "customer");
    }

    @PostMapping("/admin/apply-interest/{adminId}")
    public String manualApplyInterest(@PathVariable int adminId) {
        History h = new History();
        h.setDescription("Admin " + adminId + " manually triggered monthly interest");
        historyRepo.save(h);
        return interestScheduler.applyMonthlyInterest();
    }
}