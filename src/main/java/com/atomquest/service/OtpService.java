package com.atomquest.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {
    
    private final EmailService emailService;
    
    // In-memory store: email -> RegistrationRequest
    private final Map<String, RegistrationRequest> pendingRegistrations = new ConcurrentHashMap<>();
    
    public void generateAndSendOtp(String name, String email, String password, String role, Long managerId) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        log.info("Generated OTP for {}: {}", email, otp);
        
        RegistrationRequest req = new RegistrationRequest();
        req.setName(name);
        req.setEmail(email);
        req.setPassword(password);
        req.setRole(role);
        req.setManagerId(managerId);
        req.setOtp(otp);
        
        pendingRegistrations.put(email, req);
        
        emailService.sendOtpEmail(email, otp);
    }
    
    public RegistrationRequest verifyOtp(String email, String otp) {
        RegistrationRequest req = pendingRegistrations.get(email);
        if (req != null && req.getOtp().equals(otp)) {
            pendingRegistrations.remove(email);
            return req;
        }
        return null;
    }
    
    @Data
    public static class RegistrationRequest {
        private String name;
        private String email;
        private String password;
        private String role;
        private Long managerId;
        private String otp;
    }
}
