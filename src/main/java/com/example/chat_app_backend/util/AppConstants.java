package com.example.chat_app_backend.util;

public final class AppConstants {

  private AppConstants() {
    // Prevent instantiation
  }

  // Roles
  public static final String ROLE_USER = "ROLE_USER";
  public static final String ROLE_ADMIN = "ROLE_ADMIN";

  // Error Messages
  public static final String ERR_USERNAME_TAKEN = "Error: Username is already taken!";
  public static final String ERR_EMAIL_TAKEN = "Error: Email is already in use!";
  public static final String ERR_ADMIN_EXISTS =
      "Error: An admin already exists! Cannot register another admin.";
  public static final String ERR_USER_NOT_FOUND = "Error: User not found.";
  public static final String ERR_INVALID_CREDENTIALS = "Error: Invalid username or password!";

  // Success Messages
  public static final String MSG_USER_REGISTERED = "User registered successfully!";
  public static final String MSG_LOGOUT_SUCCESS = "Log out successful!";
}
