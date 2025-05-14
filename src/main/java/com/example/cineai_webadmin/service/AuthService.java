package com.example.cineai_webadmin.service;

import com.example.cineai_webadmin.dto.RegisterRequest;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final EmailService emailService;

    public AuthService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void register(RegisterRequest registerRequest) throws Exception {
        try {
            // Create user in Firebase Authentication
            String uid = FirebaseAuth.getInstance()
                    .createUser(new com.google.firebase.auth.UserRecord.CreateRequest()
                            .setEmail(registerRequest.getEmail())
                            .setPassword(registerRequest.getPassword())
                            .setEmailVerified(false))
                    .getUid();

            // Save user data to Firestore
            Firestore db = FirestoreClient.getFirestore();
            db.collection("users").document(uid).set(new UserData(
                    Timestamp.now(),
                    registerRequest.getEmail(),
                    registerRequest.getFullName(),
                    null, // lastLogin will be set on first login
                    registerRequest.getPhoneNumber(),
                    "",
                    "user"
            ));

            // Generate email verification link
            ActionCodeSettings actionCodeSettings = ActionCodeSettings.builder()
                    .setUrl("http://localhost:8080/verify-email")
                    .setHandleCodeInApp(true)
                    .build();

            String verificationLink = FirebaseAuth.getInstance()
                    .generateEmailVerificationLink(registerRequest.getEmail(), actionCodeSettings);

            // Send verification email
            emailService.sendVerificationEmail(registerRequest.getEmail(), verificationLink);

        } catch (Exception e) {
            throw new Exception("Registration failed: " + e.getMessage());
        }
    }

    public void login(String email, String password) throws Exception {
        try {
            // Sign in with Firebase
            UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(email);
            
            // Check if email is verified
            if (!userRecord.isEmailVerified()) {
                throw new Exception("Vui lòng xác thực email của bạn trước khi đăng nhập.");
            }

            // Update last login time
            Firestore db = FirestoreClient.getFirestore();
            db.collection("users").document(userRecord.getUid())
                .update("lastLogin", Timestamp.now());

        } catch (Exception e) {
            throw new Exception("Đăng nhập thất bại: " + e.getMessage());
        }
    }

    public static class UserData {
        private Timestamp createdAt;
        private String email;
        private String fullName;
        private Timestamp lastLogin;
        private String phoneNumber;
        private String profileImage;
        private String role;

        public UserData(Timestamp createdAt, String email, String fullName, Timestamp lastLogin,
                        String phoneNumber, String profileImage, String role) {
            this.createdAt = createdAt;
            this.email = email;
            this.fullName = fullName;
            this.lastLogin = lastLogin;
            this.phoneNumber = phoneNumber;
            this.profileImage = profileImage;
            this.role = role;
        }

        public Timestamp getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Timestamp createdAt) {
            this.createdAt = createdAt;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public Timestamp getLastLogin() {
            return lastLogin;
        }

        public void setLastLogin(Timestamp lastLogin) {
            this.lastLogin = lastLogin;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getProfileImage() {
            return profileImage;
        }

        public void setProfileImage(String profileImage) {
            this.profileImage = profileImage;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}