package com.example.chat_app_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private Integer age;

    private String region;

    public Profile() {}

    public Profile(User user) {
        this.user = user;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
