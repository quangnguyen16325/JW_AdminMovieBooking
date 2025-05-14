package com.example.cineai_webadmin.service;

import com.example.cineai_webadmin.dto.ShowtimeRequest;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;

@Service
public class ShowtimeService {
    @Autowired
    private Firestore firestore;

    public List<Map<String, Object>> getAllShowtimes() throws ExecutionException, InterruptedException {
        List<Map<String, Object>> showtimes = new ArrayList<>();
        QuerySnapshot querySnapshot = firestore.collection("showtimes").get().get();
        
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> showtime = document.getData();
            showtime.put("id", document.getId());
            
            // Lấy thông tin rạp chiếu
            DocumentSnapshot cinemaDoc = firestore.collection("cinemas").document((String) showtime.get("cinemaId")).get().get();
            if (cinemaDoc.exists()) {
                showtime.put("cinemaName", cinemaDoc.getString("name"));
            }
            
            // Lấy thông tin phim
            DocumentSnapshot movieDoc = firestore.collection("movies").document((String) showtime.get("movieId")).get().get();
            if (movieDoc.exists()) {
                showtime.put("movieName", movieDoc.getString("title"));
            }
            
            showtimes.add(showtime);
        }
        
        return showtimes;
    }

    public Map<String, Object> getShowtimeById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = firestore.collection("showtimes").document(id).get().get();
        if (document.exists()) {
            Map<String, Object> showtime = document.getData();
            showtime.put("id", document.getId());
            
            // Lấy thông tin rạp chiếu
            DocumentSnapshot cinemaDoc = firestore.collection("cinemas").document((String) showtime.get("cinemaId")).get().get();
            if (cinemaDoc.exists()) {
                showtime.put("cinemaName", cinemaDoc.getString("name"));
            }
            
            // Lấy thông tin phim
            DocumentSnapshot movieDoc = firestore.collection("movies").document((String) showtime.get("movieId")).get().get();
            if (movieDoc.exists()) {
                showtime.put("movieName", movieDoc.getString("title"));
                showtime.put("movieDuration", movieDoc.getLong("duration"));
            }
            
            return showtime;
        }
        return null;
    }

    public Map<String, Object> createShowtime(ShowtimeRequest showtime) {
        try {
            // Convert datetime strings to LocalDateTime
            LocalDateTime startDateTime = LocalDateTime.parse(showtime.getStartTime().replace(" ", "T"));
            LocalDateTime endDateTime = LocalDateTime.parse(showtime.getEndTime().replace(" ", "T"));
            
            // Validate time order
            if (endDateTime.isBefore(startDateTime)) {
                throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu");
            }

            // Check for overlapping showtimes
            if (isTimeSlotOverlapping(showtime.getCinemaId(), showtime.getScreenId(), startDateTime, endDateTime, null)) {
                throw new IllegalArgumentException("Thời gian này đã có lịch chiếu khác");
            }

            // Get movie duration from Firestore
            DocumentSnapshot movieDoc = firestore.collection("movies").document(showtime.getMovieId()).get().get();
            if (!movieDoc.exists()) {
                throw new IllegalArgumentException("Không tìm thấy phim");
            }
            Long movieDuration = movieDoc.getLong("duration");
            if (movieDuration == null) {
                throw new IllegalArgumentException("Phim không có thông tin thời lượng");
            }

            // Validate showtime duration matches movie duration
            long showtimeDuration = ChronoUnit.MINUTES.between(startDateTime, endDateTime);
            if (showtimeDuration != movieDuration) {
                throw new IllegalArgumentException("Thời lượng lịch chiếu phải bằng thời lượng phim");
            }

            // Create showtime document
            Map<String, Object> showtimeData = new HashMap<>();
            showtimeData.put("cinemaId", showtime.getCinemaId());
            showtimeData.put("movieId", showtime.getMovieId());
            showtimeData.put("screenId", showtime.getScreenId());
            showtimeData.put("startTime", Timestamp.of(Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant())));
            showtimeData.put("endTime", Timestamp.of(Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant())));
            showtimeData.put("format", showtime.getFormat());
            showtimeData.put("price", showtime.getPrice());
            showtimeData.put("availableSeats", showtime.getAvailableSeats());

            // Add cinema and movie names
            DocumentSnapshot cinemaDoc = firestore.collection("cinemas").document(showtime.getCinemaId()).get().get();
            if (cinemaDoc.exists()) {
                showtimeData.put("cinemaName", cinemaDoc.getString("name"));
            }

            showtimeData.put("movieName", movieDoc.getString("title"));

            // Save to Firestore
            DocumentReference docRef = firestore.collection("showtimes").document();
            docRef.set(showtimeData).get();

            // Add ID to response
            showtimeData.put("id", docRef.getId());
            return showtimeData;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Định dạng thời gian không hợp lệ");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Lỗi khi tạo lịch chiếu: " + e.getMessage());
        }
    }

    public void updateShowtime(String id, ShowtimeRequest request) throws ExecutionException, InterruptedException {
        try {
            // Convert datetime strings to LocalDateTime
            LocalDateTime startDateTime = LocalDateTime.parse(request.getStartTime().replace(" ", "T"));
            LocalDateTime endDateTime = LocalDateTime.parse(request.getEndTime().replace(" ", "T"));
            
            // Validate time order
            if (endDateTime.isBefore(startDateTime)) {
                throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu");
            }

            // Get movie duration from Firestore
            DocumentSnapshot movieDoc = firestore.collection("movies").document(request.getMovieId()).get().get();
            if (!movieDoc.exists()) {
                throw new IllegalArgumentException("Không tìm thấy phim");
            }
            Long movieDuration = movieDoc.getLong("duration");
            if (movieDuration == null) {
                throw new IllegalArgumentException("Phim không có thông tin thời lượng");
            }

            // Validate showtime duration matches movie duration
            long showtimeDuration = ChronoUnit.MINUTES.between(startDateTime, endDateTime);
            if (showtimeDuration != movieDuration) {
                throw new IllegalArgumentException("Thời lượng lịch chiếu phải bằng thời lượng phim");
            }

            // Check for overlapping showtimes (excluding current showtime)
            if (isTimeSlotOverlapping(request.getCinemaId(), request.getScreenId(), startDateTime, endDateTime, id)) {
                throw new IllegalArgumentException("Thời gian này đã có lịch chiếu khác");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("cinemaId", request.getCinemaId());
            updates.put("movieId", request.getMovieId());
            updates.put("screenId", request.getScreenId());
            updates.put("startTime", Timestamp.of(Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant())));
            updates.put("endTime", Timestamp.of(Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant())));
            updates.put("format", request.getFormat());
            updates.put("price", request.getPrice());
            updates.put("availableSeats", request.getAvailableSeats());

            // Add cinema and movie names
            DocumentSnapshot cinemaDoc = firestore.collection("cinemas").document(request.getCinemaId()).get().get();
            if (cinemaDoc.exists()) {
                updates.put("cinemaName", cinemaDoc.getString("name"));
            }
            updates.put("movieName", movieDoc.getString("title"));

            firestore.collection("showtimes").document(id).update(updates).get();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Định dạng thời gian không hợp lệ");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Lỗi khi cập nhật lịch chiếu: " + e.getMessage());
        }
    }

    public void deleteShowtime(String id) throws ExecutionException, InterruptedException {
        firestore.collection("showtimes").document(id).delete().get();
    }

    public boolean isTimeSlotOverlapping(String cinemaId, String screenId, LocalDateTime newStart, LocalDateTime newEnd, String excludeShowtimeId) {
        try {
            // Convert to Timestamp for comparison
            Timestamp newStartTimestamp = Timestamp.of(Date.from(newStart.atZone(ZoneId.systemDefault()).toInstant()));
            Timestamp newEndTimestamp = Timestamp.of(Date.from(newEnd.atZone(ZoneId.systemDefault()).toInstant()));

            // Query existing showtimes
            Query query = firestore.collection("showtimes")
                    .whereEqualTo("cinemaId", cinemaId)
                    .whereEqualTo("screenId", screenId);

            QuerySnapshot querySnapshot = query.get().get();
            for (QueryDocumentSnapshot doc : querySnapshot.getDocuments()) {
                // Bỏ qua lịch chiếu hiện tại khi cập nhật
                if (excludeShowtimeId != null && doc.getId().equals(excludeShowtimeId)) {
                    continue;
                }

                Timestamp existingStart = doc.getTimestamp("startTime");
                Timestamp existingEnd = doc.getTimestamp("endTime");

                // Check for overlap
                if (newStartTimestamp.compareTo(existingEnd) < 0 && newEndTimestamp.compareTo(existingStart) > 0) {
                    return true;
                }
            }
            return false;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Lỗi khi kiểm tra lịch chiếu trùng lặp: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getScreensByCinema(String cinemaId) throws ExecutionException, InterruptedException {
        List<Map<String, Object>> screens = new ArrayList<>();
        QuerySnapshot querySnapshot = firestore.collection("cinemas")
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

    private boolean checkTimeConflict(String cinemaId, String screenId, Timestamp startTime, Timestamp endTime, String excludeShowtimeId) throws ExecutionException, InterruptedException {
        // Lấy tất cả các suất chiếu của rạp
        Query query = firestore.collection("showtimes")
                .whereEqualTo("cinemaId", cinemaId);
        
        QuerySnapshot querySnapshot = query.get().get();
        
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            // Bỏ qua suất chiếu hiện tại khi cập nhật
            if (excludeShowtimeId != null && document.getId().equals(excludeShowtimeId)) {
                continue;
            }
            
            Map<String, Object> showtime = document.getData();
            
            // Kiểm tra phòng chiếu
            if (!showtime.get("screenId").equals(screenId)) {
                continue;
            }
            
            // Kiểm tra thời gian
            Timestamp existingStartTime = (Timestamp) showtime.get("startTime");
            Timestamp existingEndTime = (Timestamp) showtime.get("endTime");
            
            // Kiểm tra xem có xung đột thời gian không
            if (!(endTime.compareTo(existingStartTime) <= 0 || startTime.compareTo(existingEndTime) >= 0)) {
                return true; // Có xung đột
            }
        }
        
        return false; // Không có xung đột
    }
} 