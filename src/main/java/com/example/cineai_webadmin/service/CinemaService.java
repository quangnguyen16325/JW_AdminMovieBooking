package com.example.cineai_webadmin.service;

import com.example.cineai_webadmin.dto.CinemaRequest;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class CinemaService {
    private final Firestore firestore;
    private static final String COLLECTION_NAME = "cinemas";

    @Autowired
    public CinemaService(Firestore firestore) {
        this.firestore = firestore;
    }

    public List<Map<String, Object>> getAllCinemas() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION_NAME).get().get();
        List<Map<String, Object>> cinemas = new ArrayList<>();
        for (QueryDocumentSnapshot document : querySnapshot) {
            Map<String, Object> cinema = document.getData();
            cinema.put("id", document.getId());
            cinemas.add(cinema);
        }
        return cinemas;
    }

    public Map<String, Object> getCinemaById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(id).get().get();
        if (document.exists()) {
            Map<String, Object> cinema = document.getData();
            cinema.put("id", document.getId());
            return cinema;
        }
        return null;
    }

    public String createCinema(CinemaRequest cinemaRequest) throws ExecutionException, InterruptedException {
        // Xử lý facilities từ chuỗi được ngăn cách bởi dấu phẩy
        List<String> facilitiesList = Arrays.asList(cinemaRequest.getFacilities().split("\\s*,\\s*"));

        Map<String, Object> cinema = new HashMap<>();
        cinema.put("name", cinemaRequest.getName());
        cinema.put("address", cinemaRequest.getAddress());
        cinema.put("city", cinemaRequest.getCity());
        cinema.put("imageUrl", cinemaRequest.getImageUrl());
        cinema.put("facilities", facilitiesList);
        cinema.put("numberOfScreens", cinemaRequest.getNumberOfScreens());
        cinema.put("createAt", Timestamp.now());

        // Tạo map location
        Map<String, Object> location = new HashMap<>();
        location.put("latitude", cinemaRequest.getLatitude());
        location.put("longitude", cinemaRequest.getLongitude());
        cinema.put("location", location);

        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document();
        docRef.set(cinema).get();

        // Tạo các màn hình chiếu
        createScreens(docRef.getId(), cinemaRequest.getNumberOfScreens());

        return docRef.getId();
    }

    public void updateCinema(String id, CinemaRequest cinemaRequest) throws ExecutionException, InterruptedException {
        // Xử lý facilities từ chuỗi được ngăn cách bởi dấu phẩy
        List<String> facilitiesList = Arrays.asList(cinemaRequest.getFacilities().split("\\s*,\\s*"));

        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(id);
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", cinemaRequest.getName());
        updates.put("address", cinemaRequest.getAddress());
        updates.put("city", cinemaRequest.getCity());
        updates.put("imageUrl", cinemaRequest.getImageUrl());
        updates.put("facilities", facilitiesList);
        updates.put("numberOfScreens", cinemaRequest.getNumberOfScreens());

        // Tạo map location
        Map<String, Object> location = new HashMap<>();
        location.put("latitude", cinemaRequest.getLatitude());
        location.put("longitude", cinemaRequest.getLongitude());
        updates.put("location", location);

        docRef.update(updates).get();

        // Cập nhật số lượng màn hình chiếu
        updateScreens(id, cinemaRequest.getNumberOfScreens());
    }

    private void createScreens(String cinemaId, int numberOfScreens) throws ExecutionException, InterruptedException {
        CollectionReference screensRef = firestore.collection(COLLECTION_NAME).document(cinemaId).collection("screens");
        
        // Xóa các màn hình cũ nếu có
        QuerySnapshot existingScreens = screensRef.get().get();
        for (QueryDocumentSnapshot screen : existingScreens) {
            screen.getReference().delete().get();
        }

        // Tạo các màn hình mới
        for (int i = 1; i <= numberOfScreens; i++) {
            Map<String, Object> screen = new HashMap<>();
            screen.put("name", "Screen " + i);
            screen.put("capacity", 72); // Sức chứa mặc định
            screen.put("type", "Standard"); // Loại màn hình mặc định
            screen.put("createAt", Timestamp.now());
//            screensRef.document("screen" + i).set(screen).get();
            // Lưu với ID là chuỗi số: "1", "2", ...
            screensRef.document(String.valueOf(i)).set(screen).get();
        }
    }

    private void updateScreens(String cinemaId, int numberOfScreens) throws ExecutionException, InterruptedException {
        createScreens(cinemaId, numberOfScreens);
    }

    public void deleteCinema(String id) throws ExecutionException, InterruptedException {
        // Xóa tất cả các màn hình trước
        CollectionReference screensRef = firestore.collection(COLLECTION_NAME).document(id).collection("screens");
        QuerySnapshot screens = screensRef.get().get();
        for (QueryDocumentSnapshot screen : screens) {
            screen.getReference().delete().get();
        }
        
        // Sau đó xóa rạp chiếu
        firestore.collection(COLLECTION_NAME).document(id).delete().get();
    }

    public List<Map<String, Object>> getScreensByCinemaId(String cinemaId) throws ExecutionException, InterruptedException {
        List<Map<String, Object>> screens = new ArrayList<>();
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION_NAME)
                .document(cinemaId)
                .collection("screens")
                .get()
                .get();
        
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> screen = document.getData();
            screen.put("id", document.getId());
            screens.add(screen);
        }
        
        return screens;
    }
} 