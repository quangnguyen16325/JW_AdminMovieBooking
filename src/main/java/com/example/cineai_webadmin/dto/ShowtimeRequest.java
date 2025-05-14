package com.example.cineai_webadmin.dto;

public class ShowtimeRequest {
    private String cinemaId;
    private String movieId;
    private String screenId;
    private String startTime;
    private String endTime;
    private String format;
    private Double price;
    private Number availableSeats = 72; // Mac dinh 72

    public Number getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(Number availableSeats) {
        this.availableSeats = availableSeats;
    }

    public String getCinemaId() {
        return cinemaId;
    }

    public void setCinemaId(String cinemaId) {
        this.cinemaId = cinemaId;
    }

    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = movieId;
    }

    public String getScreenId() {
        return screenId;
    }

    public void setScreenId(String screenId) {
        this.screenId = screenId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
} 