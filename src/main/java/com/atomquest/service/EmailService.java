package com.atomquest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String to, String otp) {
        log.info("========================================");
        log.info("SYSTEM OTP INTERCEPT FOR: {}", to);
        log.info("YOUR REGISTRATION OTP IS: {}", otp);
        log.info("========================================");
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("AtomQuest - Your Registration OTP");
            message.setText("Welcome to AtomQuest!\n\nYour registration OTP is: " + otp + "\n\nPlease enter this to complete your registration.");
            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (Exception e) {
            log.warn("Failed to send real email to {}. Users can still use the intercepted OTP logged above. Error: {}", to, e.getMessage());
        }
    }
    public void sendGoalUpdateEmail(String to, String subject, String content) {
        log.info("========================================");
        log.info("SYSTEM EMAIL INTERCEPT FOR: {}", to);
        log.info("SUBJECT: {}", subject);
        log.info("CONTENT: {}", content);
        log.info("========================================");
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (Exception e) {
            log.warn("Failed to send real email to {}. Error: {}", to, e.getMessage());
        }
    }
}
