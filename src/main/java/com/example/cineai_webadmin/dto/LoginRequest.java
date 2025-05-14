package com.example.cineai_webadmin.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}
