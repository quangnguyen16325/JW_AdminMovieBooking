package com.example.cineai_webadmin.service;

import com.example.cineai_webadmin.dto.MovieRequest;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class MovieService {
    private final Firestore firestore;
    private static final String COLLECTION_NAME = "movies";

    @Autowired
    public MovieService(Firestore firestore) {
        this.firestore = firestore;
    }

    public List<Map<String, Object>> getAllMovies() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION_NAME).get().get();
        List<Map<String, Object>> movies = new ArrayList<>();
        for (QueryDocumentSnapshot document : querySnapshot) {
            Map<String, Object> movie = document.getData();
            movie.put("id", document.getId());
            movies.add(movie);
        }
        return movies;
    }

    public Map<String, Object> getMovieById(String id) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(id).get().get();
        if (document.exists()) {
            Map<String, Object> movie = document.getData();
            movie.put("id", document.getId());
            return movie;
        }
        return null;
    }

    public String createMovie(MovieRequest movieRequest) throws ExecutionException, InterruptedException {
        // Validate movie status
        if (movieRequest.isNowShowing() && movieRequest.isComingSoon()) {
            throw new IllegalArgumentException("Phim không thể vừa đang chiếu vừa sắp chiếu");
        }

        // Xử lý cast và genres từ chuỗi được ngăn cách bởi dấu phẩy
        List<String> castList = Arrays.asList(movieRequest.getCast().split("\\s*,\\s*"));
        List<String> genresList = Arrays.asList(movieRequest.getGenres().split("\\s*,\\s*"));

        Map<String, Object> movie = new HashMap<>();
        movie.put("title", movieRequest.getTitle());
        movie.put("overview", movieRequest.getOverview());
        movie.put("posterUrl", movieRequest.getPosterUrl());
        movie.put("backdropUrl", movieRequest.getBackdropUrl());
        movie.put("trailerUrl", movieRequest.getTrailerUrl());
        movie.put("director", movieRequest.getDirector());
        movie.put("cast", castList);
        movie.put("genres", genresList);
        movie.put("duration", movieRequest.getDuration());
        movie.put("rating", movieRequest.getRating());
        movie.put("isNowShowing", movieRequest.isNowShowing());
        movie.put("isComingSoon", movieRequest.isComingSoon());
        movie.put("createAt", Timestamp.now());

        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document();
        docRef.set(movie).get();
        return docRef.getId();
    }

    public void updateMovie(String id, MovieRequest movieRequest) throws ExecutionException, InterruptedException {
        // Validate movie status
        if (movieRequest.isNowShowing() && movieRequest.isComingSoon()) {
            throw new IllegalArgumentException("Phim không thể vừa đang chiếu vừa sắp chiếu");
        }

        // Xử lý cast và genres từ chuỗi được ngăn cách bởi dấu phẩy
        List<String> castList = Arrays.asList(movieRequest.getCast().split("\\s*,\\s*"));
        List<String> genresList = Arrays.asList(movieRequest.getGenres().split("\\s*,\\s*"));

        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(id);
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", movieRequest.getTitle());
        updates.put("overview", movieRequest.getOverview());
        updates.put("posterUrl", movieRequest.getPosterUrl());
        updates.put("backdropUrl", movieRequest.getBackdropUrl());
        updates.put("trailerUrl", movieRequest.getTrailerUrl());
        updates.put("director", movieRequest.getDirector());
        updates.put("cast", castList);
        updates.put("genres", genresList);
        updates.put("duration", movieRequest.getDuration());
        updates.put("rating", movieRequest.getRating());
        updates.put("isNowShowing", movieRequest.isNowShowing());
        updates.put("isComingSoon", movieRequest.isComingSoon());

        docRef.update(updates).get();
    }

    public void deleteMovie(String id) throws ExecutionException, InterruptedException {
        firestore.collection(COLLECTION_NAME).document(id).delete().get();
    }

    public List<Map<String, Object>> getNowShowingMovies() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("isNowShowing", true)
                .get()
                .get();
        List<Map<String, Object>> movies = new ArrayList<>();
        for (QueryDocumentSnapshot document : querySnapshot) {
            Map<String, Object> movie = document.getData();
            movie.put("id", document.getId());
            movies.add(movie);
        }
        return movies;
    }

    public List<Map<String, Object>> getComingSoonMovies() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("isComingSoon", true)
                .get()
                .get();
        List<Map<String, Object>> movies = new ArrayList<>();
        for (QueryDocumentSnapshot document : querySnapshot) {
            Map<String, Object> movie = document.getData();
            movie.put("id", document.getId());
            movies.add(movie);
        }
        return movies;
    }

    public long getNowShowingMoviesCount() throws ExecutionException, InterruptedException {
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("isNowShowing", true)
                .get()
                .get();
        return querySnapshot.size();
    }

    public long getNewMoviesCount() throws ExecutionException, InterruptedException {
        // Lấy thời gian 7 ngày trước
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Timestamp sevenDaysAgo = Timestamp.of(calendar.getTime());

        // Lấy tất cả phim đang chiếu
        QuerySnapshot querySnapshot = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("isNowShowing", true)
                .get()
                .get();

        // Đếm số phim mới trong 7 ngày
        long count = 0;
        for (QueryDocumentSnapshot document : querySnapshot) {
            Timestamp createAt = document.getTimestamp("createAt");
            if (createAt != null && createAt.compareTo(sevenDaysAgo) > 0) {
                count++;
            }
        }
        return count;
    }
} 