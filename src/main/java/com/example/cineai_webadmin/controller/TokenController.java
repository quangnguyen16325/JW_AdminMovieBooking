package com.example.cineai_webadmin.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TokenController {

    private static final Logger logger = LoggerFactory.getLogger(TokenController.class);

    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyToken(HttpServletRequest request, HttpSession session) {
        try {
            String idToken = request.getHeader("Authorization");
            if (idToken == null || !idToken.startsWith("Bearer ")) {
                logger.error("Invalid Authorization header: {}", idToken);
                return ResponseEntity.badRequest().body(new VerifyTokenResponse(false, "Invalid Authorization header", null));
            }
            idToken = idToken.replace("Bearer ", "");
            logger.info("Received token: {}", idToken);
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            logger.info("Token verified for user: {}", decodedToken.getUid());
            // Check Firestore for user role
            Firestore db = FirestoreClient.getFirestore();
            String redirectUrl = null;
            // Check admin collection first
            DocumentSnapshot adminDoc = db.collection("admin").document(decodedToken.getUid()).get().get();
            if (adminDoc.exists() && "admin".equals(adminDoc.getString("role"))) {
                redirectUrl = "/admin/dashboard";
                session.setAttribute("role", "admin");
                session.setAttribute("email", adminDoc.getString("email"));
            } else {
                // Check users collection
                DocumentSnapshot userDoc = db.collection("users").document(decodedToken.getUid()).get().get();
                if (userDoc.exists() && "user".equals(userDoc.getString("role"))) {
                    redirectUrl = "/home";
                    // Update lastLogin
                    db.collection("users").document(decodedToken.getUid())
                            .update("lastLogin", Timestamp.now());
                    session.setAttribute("role", "user");
                    session.setAttribute("fullName", userDoc.getString("fullName"));
                }
            }
            if (redirectUrl == null) {
                logger.warn("User {} not found in Firestore", decodedToken.getUid());
                return ResponseEntity.badRequest().body(new VerifyTokenResponse(false, "User not found", null));
            }
            // Store user in session
            session.setAttribute("userId", decodedToken.getUid());
            logger.info("Session ID: {}", session.getId());
            return ResponseEntity.ok().body(new VerifyTokenResponse(true, "Token verified", redirectUrl));
        } catch (Exception e) {
            logger.error("Token verification failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(new VerifyTokenResponse(false, e.getMessage(), null));
        }
    }

    public static class VerifyTokenResponse {
        private boolean success;
        private String message;
        private String redirectUrl;

        public VerifyTokenResponse(boolean success, String message, String redirectUrl) {
            this.success = success;
            this.message = message;
            this.redirectUrl = redirectUrl;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getRedirectUrl() {
            return redirectUrl;
        }

        public void setRedirectUrl(String redirectUrl) {
            this.redirectUrl = redirectUrl;
        }
    }
}