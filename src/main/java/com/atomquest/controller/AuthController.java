package com.atomquest.controller;

import com.atomquest.model.User;
import com.atomquest.repository.UserRepository;
import com.atomquest.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("managers", userRepository.findByRole(User.Role.MANAGER));
        return "auth/register";
    }

    @PostMapping("/register")
    public String submitRegistration(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String role,
            @RequestParam(required = false) Long managerId,
            Model model) {
        
        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error", "Email already exists.");
            model.addAttribute("managers", userRepository.findByRole(User.Role.MANAGER));
            return "auth/register";
        }

        otpService.generateAndSendOtp(name, email, password, role, managerId);
        return "redirect:/verify-otp?email=" + email;
    }

    @GetMapping("/verify-otp")
    public String verifyOtpForm(@RequestParam String email, Model model) {
        model.addAttribute("email", email);
        return "auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String submitVerifyOtp(
            @RequestParam String email,
            @RequestParam String otp,
            Model model) {
        
        OtpService.RegistrationRequest req = otpService.verifyOtp(email, otp);
        if (req == null) {
            model.addAttribute("error", "Invalid or expired OTP.");
            model.addAttribute("email", email);
            return "auth/verify-otp";
        }

        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(User.Role.valueOf(req.getRole()));
        user.setActive(true);
        
        if (req.getManagerId() != null) {
            userRepository.findById(req.getManagerId()).ifPresent(user::setManager);
        }
        
        userRepository.save(user);
        
        return "redirect:/login?registered=true";
    }
}
