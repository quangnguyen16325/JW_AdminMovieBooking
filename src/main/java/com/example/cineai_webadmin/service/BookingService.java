package com.example.cineai_webadmin.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.ZonedDateTime;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class BookingService {
    private final Firestore firestore;
    private final CollectionReference bookingsCollection;
    private final CollectionReference moviesCollection;
    private final CollectionReference cinemasCollection;
    private final CollectionReference showtimesCollection;
    private final CollectionReference usersCollection;

    @Autowired
    public BookingService(Firestore firestore) {
        this.firestore = firestore;
        this.bookingsCollection = firestore.collection("bookings");
        this.moviesCollection = firestore.collection("movies");
        this.cinemasCollection = firestore.collection("cinemas");
        this.showtimesCollection = firestore.collection("showtimes");
        this.usersCollection = firestore.collection("users");
    }

    public List<Map<String, Object>> getAllBookings() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = bookingsCollection.get().get();
        List<Map<String, Object>> bookings = new ArrayList<>();
        
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> booking = document.getData();
            booking.put("id", document.getId());
            
            // Lấy thông tin chi tiết
            enrichBookingDetails(booking);
            
            bookings.add(booking);
        }
        
        return bookings;
    }

    public List<Map<String, Object>> searchBookings(String searchTerm, String status, String paymentMethod, String sortBy) throws ExecutionException, InterruptedException {
        Query query = bookingsCollection;
        
        // Áp dụng bộ lọc trạng thái
        if (status != null && !status.isEmpty()) {
            query = query.whereEqualTo("status", status);
        }
        
        // Áp dụng bộ lọc phương thức thanh toán
        if (paymentMethod != null && !paymentMethod.isEmpty()) {
            query = query.whereEqualTo("paymentMethod", paymentMethod);
        }
        
        // Lấy tất cả booking
        QuerySnapshot querySnapshot = query.get().get();
        List<Map<String, Object>> bookings = new ArrayList<>();
        
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> booking = document.getData();
            booking.put("id", document.getId());
            
            // Lấy thông tin chi tiết
            enrichBookingDetails(booking);
            
            // Lọc theo tên người dùng nếu có từ khóa tìm kiếm
            if (searchTerm != null && !searchTerm.isEmpty()) {
                String userName = (String) booking.get("userName");
                if (userName != null && userName.toLowerCase().contains(searchTerm.toLowerCase())) {
                    bookings.add(booking);
                }
            } else {
                bookings.add(booking);
            }
        }
        
        // Sắp xếp kết quả
        if (sortBy != null && !sortBy.isEmpty()) {
            String[] sortParams = sortBy.split("_");
            String field = sortParams[0];
            String direction = sortParams[1];
            
            bookings.sort((a, b) -> {
                Object valueA = a.get(field);
                Object valueB = b.get(field);
                
                if (valueA instanceof Timestamp && valueB instanceof Timestamp) {
                    Timestamp tsA = (Timestamp) valueA;
                    Timestamp tsB = (Timestamp) valueB;
                    return direction.equals("asc") ? 
                        tsA.compareTo(tsB) : tsB.compareTo(tsA);
                } else if (valueA instanceof Number && valueB instanceof Number) {
                    double numA = ((Number) valueA).doubleValue();
                    double numB = ((Number) valueB).doubleValue();
                    return direction.equals("asc") ? 
                        Double.compare(numA, numB) : Double.compare(numB, numA);
                }
                return 0;
            });
        }
        
        return bookings;
    }

    public List<Map<String, Object>> filterBookings(Map<String, String> filters) throws ExecutionException, InterruptedException {
        Query query = bookingsCollection;
        
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            query = query.whereEqualTo(filter.getKey(), filter.getValue());
        }
        
        QuerySnapshot querySnapshot = query.get().get();
        List<Map<String, Object>> bookings = new ArrayList<>();
        
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> booking = document.getData();
            booking.put("id", document.getId());
            
            // Lấy thông tin chi tiết
            enrichBookingDetails(booking);
            
            bookings.add(booking);
        }
        
        return bookings;
    }

    public Map<String, Object> getBookingById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = bookingsCollection.document(id).get().get();
        
        if (!document.exists()) {
            throw new RuntimeException("Booking not found");
        }
        
        Map<String, Object> booking = document.getData();
        booking.put("id", document.getId());
        
        // Lấy thông tin chi tiết
        enrichBookingDetails(booking);
        
        return booking;
    }

    public void updateBooking(String id, Map<String, Object> bookingData) throws ExecutionException, InterruptedException {
        DocumentReference docRef = bookingsCollection.document(id);
        docRef.update(bookingData).get();
    }

    private void enrichBookingDetails(Map<String, Object> booking) throws ExecutionException, InterruptedException {
        // Lấy thông tin phim
        String movieId = (String) booking.get("movieId");
        if (movieId != null) {
            DocumentSnapshot movieDoc = moviesCollection.document(movieId).get().get();
            if (movieDoc.exists()) {
                Map<String, Object> movieData = movieDoc.getData();
                booking.put("movieTitle", movieData.get("title"));
                booking.put("moviePoster", movieData.get("poster"));
                booking.put("movieDuration", movieData.get("duration"));
                booking.put("movieGenre", movieData.get("genres"));
            }
        }

        // Lấy thông tin rạp chiếu
        String cinemaId = (String) booking.get("cinemaId");
        if (cinemaId != null) {
            DocumentSnapshot cinemaDoc = cinemasCollection.document(cinemaId).get().get();
            if (cinemaDoc.exists()) {
                Map<String, Object> cinemaData = cinemaDoc.getData();
                booking.put("cinemaName", cinemaData.get("name"));
                booking.put("cinemaAddress", cinemaData.get("address"));
//                booking.put("cinemaPhone", cinemaData.get("phone"));
            }
        }

        // Lấy thông tin suất chiếu
        String showtimeId = (String) booking.get("showtimeId");
        if (showtimeId != null) {
            DocumentSnapshot showtimeDoc = showtimesCollection.document(showtimeId).get().get();
            if (showtimeDoc.exists()) {
                Map<String, Object> showtimeData = showtimeDoc.getData();
                Object startTimeObj = showtimeData.get("startTime");
                if (startTimeObj instanceof Timestamp) {
                    Timestamp timestamp = (Timestamp) startTimeObj;
                    ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                        Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()),
                        ZoneId.of("Asia/Ho_Chi_Minh")
                    );
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
                    booking.put("showtimeTime", dateTime.format(formatter));
                } else if (startTimeObj instanceof String) {
                    String startTime = (String) startTimeObj;
                    Instant instant = Instant.parse(startTime);
                    ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                        instant,
                        ZoneId.of("Asia/Ho_Chi_Minh")
                    );
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
                    booking.put("showtimeTime", dateTime.format(formatter));
                }
                booking.put("showtimeRoom", showtimeData.get("screenId"));
                booking.put("showtimePrice", showtimeData.get("price"));
            }
        }

        // Lấy thông tin người dùng
        String userId = (String) booking.get("userId");
        if (userId != null) {
            DocumentSnapshot userDoc = usersCollection.document(userId).get().get();
            if (userDoc.exists()) {
                Map<String, Object> userData = userDoc.getData();
                booking.put("userName", userData.get("fullName"));
                booking.put("userEmail", userData.get("email"));
                booking.put("userPhone", userData.get("phoneNumber"));
            }
        }

        // Định dạng ghế
        List<String> seats = (List<String>) booking.get("seats");
        if (seats != null) {
            List<String> formattedSeats = seats.stream()
                .map(seat -> {
                    // Tách chuỗi ghế (ví dụ: showtime1_G_5 -> G5)
                    String[] parts = seat.split("_");
                    if (parts.length >= 3) {
                        return parts[1] + parts[2];
                    }
                    return seat;
                })
                .collect(Collectors.toList());
            booking.put("formattedSeats", formattedSeats);
        }

        // Định dạng ngày đặt vé
        Object bookingDateObj = booking.get("bookingDate");
        if (bookingDateObj instanceof Timestamp) {
            Timestamp timestamp = (Timestamp) bookingDateObj;
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()),
                ZoneId.of("Asia/Ho_Chi_Minh")
            );
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
            booking.put("formattedBookingDate", dateTime.format(formatter));
        } else if (bookingDateObj instanceof String) {
            String bookingDate = (String) bookingDateObj;
            Instant instant = Instant.parse(bookingDate);
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                instant,
                ZoneId.of("Asia/Ho_Chi_Minh")
            );
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
            booking.put("formattedBookingDate", dateTime.format(formatter));
        }
    }

    public double getTotalRevenue() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = bookingsCollection
            .whereEqualTo("status", "CONFIRMED")
            .get()
            .get();
        
        double totalRevenue = 0;
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> booking = document.getData();
            Double amount = (Double) booking.get("totalAmount");
            if (amount != null) {
                totalRevenue += amount;
            }
        }
        return totalRevenue;
    }

    public long getTotalTickets() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = bookingsCollection
            .whereEqualTo("status", "CONFIRMED")
            .get()
            .get();
        
        return querySnapshot.size();
    }

    public Map<String, Double> getMonthlyRevenue() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = bookingsCollection
            .whereEqualTo("status", "CONFIRMED")
            .get()
            .get();
        
        Map<String, Double> monthlyRevenue = new TreeMap<>();
        for (int i = 1; i <= 12; i++) {
            monthlyRevenue.put(String.format("T%d", i), 0.0);
        }
        
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> booking = document.getData();
            Timestamp bookingDate = (Timestamp) booking.get("bookingDate");
            if (bookingDate != null) {
                LocalDateTime date = bookingDate.toDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
                String month = String.format("T%d", date.getMonthValue());
                Double amount = (Double) booking.get("totalAmount");
                if (amount != null) {
                    monthlyRevenue.put(month, monthlyRevenue.get(month) + amount);
                }
            }
        }
        return monthlyRevenue;
    }

    public List<Map<String, Object>> getPopularMovies() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = bookingsCollection
            .whereEqualTo("status", "CONFIRMED")
            .get()
            .get();
        
        Map<String, Long> movieTickets = new HashMap<>();
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> booking = document.getData();
            String movieId = (String) booking.get("movieId");
            List<Map<String, Object>> tickets = (List<Map<String, Object>>) booking.get("tickets");
            if (movieId != null && tickets != null) {
                movieTickets.merge(movieId, (long) tickets.size(), Long::sum);
            }
        }
        
        return movieTickets.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(entry -> {
                Map<String, Object> movie = new HashMap<>();
                movie.put("id", entry.getKey());
                movie.put("tickets", entry.getValue());
                try {
                    DocumentSnapshot movieDoc = moviesCollection.document(entry.getKey()).get().get();
                    if (movieDoc.exists()) {
                        movie.put("title", movieDoc.getString("title"));
                    }
                } catch (Exception e) {
                    // Ignore movie details if not found
                }
                return movie;
            })
            .collect(Collectors.toList());
    }

    public Map<String, Long> getHourlyStats() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = bookingsCollection
            .whereEqualTo("status", "CONFIRMED")
            .get()
            .get();
        
        Map<String, Long> hourlyStats = new TreeMap<>();
        for (int i = 0; i < 24; i++) {
            hourlyStats.put(String.format("%02d:00", i), 0L);
        }
        
        for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
            Map<String, Object> booking = document.getData();
            Timestamp bookingDate = (Timestamp) booking.get("bookingDate");
            if (bookingDate != null) {
                LocalDateTime date = bookingDate.toDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
                String hour = String.format("%02d:00", date.getHour());
                List<Map<String, Object>> tickets = (List<Map<String, Object>>) booking.get("tickets");
                if (tickets != null) {
                    hourlyStats.put(hour, hourlyStats.get(hour) + tickets.size());
                }
            }
        }
        return hourlyStats;
    }
} 