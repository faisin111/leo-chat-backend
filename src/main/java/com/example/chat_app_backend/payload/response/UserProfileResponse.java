package com.example.chat_app_backend.payload.response;

import java.util.UUID;

public class UserProfileResponse {
    private UUID id;
    private String username;
    private String email;
    private String role;
    
    private String phoneNumber;
    private String bio;
    private Integer age;
    private String region;

    public UserProfileResponse(UUID id, String username, String email, String role, 
                               String phoneNumber, String bio, Integer age, String region) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
        this.phoneNumber = phoneNumber;
        this.bio = bio;
        this.age = age;
        this.region = region;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getBio() { return bio; }
    public Integer getAge() { return age; }
    public String getRegion() { return region; }
}
