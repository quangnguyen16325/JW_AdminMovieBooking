package com.example.cineai_webadmin.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class UserService {
    private final Firestore firestore;
    private final CollectionReference usersCollection;

    @Autowired
    public UserService(Firestore firestore) {
        this.firestore = firestore;
        this.usersCollection = firestore.collection("users");
    }

    public long getTotalUsers() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = usersCollection.get().get();
        return querySnapshot.size();
    }

    public List<Map<String, Object>> getAllUsers() {
        try {
            List<QueryDocumentSnapshot> documents = firestore.collection("users").get().get().getDocuments();
            List<Map<String, Object>> users = new ArrayList<>();
            
            for (QueryDocumentSnapshot document : documents) {
                Map<String, Object> userData = document.getData();
                userData.put("id", document.getId());
                users.add(userData);
            }
            
            return users;
        } catch (Exception e) {
            throw new RuntimeException("Error getting users: " + e.getMessage());
        }
    }

    public Map<String, Object> getUserById(String id) {
        try {
            var docSnap = firestore.collection("users").document(id).get().get();
            Map<String, Object> userData = docSnap.getData();
            if (userData != null) {
                userData.put("id", id);
            }
            return userData;
        } catch (Exception e) {
            throw new RuntimeException("Error getting user: " + e.getMessage());
        }
    }

    public void updateUser(String id, Map<String, Object> userData) {
        try {
            firestore.collection("users").document(id).update(userData);
        } catch (Exception e) {
            throw new RuntimeException("Error updating user: " + e.getMessage());
        }
    }

    public void deleteUser(String id) {
        try {
            firestore.collection("users").document(id).delete();
        } catch (Exception e) {
            throw new RuntimeException("Error deleting user: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> searchUsers(String query) {
        try {
            List<QueryDocumentSnapshot> documents = firestore.collection("users").get().get().getDocuments();
            List<Map<String, Object>> users = new ArrayList<>();
            
            for (QueryDocumentSnapshot document : documents) {
                Map<String, Object> userData = document.getData();
                String fullName = String.valueOf(userData.get("fullName")).toLowerCase();
                String email = String.valueOf(userData.get("email")).toLowerCase();
                String phoneNumber = String.valueOf(userData.get("phoneNumber")).toLowerCase();
                
                if (fullName.contains(query.toLowerCase()) || 
                    email.contains(query.toLowerCase()) || 
                    phoneNumber.contains(query.toLowerCase())) {
                    userData.put("id", document.getId());
                    users.add(userData);
                }
            }
            
            return users;
        } catch (Exception e) {
            throw new RuntimeException("Error searching users: " + e.getMessage());
        }
    }
} 