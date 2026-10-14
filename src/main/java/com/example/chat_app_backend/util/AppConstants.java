package com.example.chat_app_backend.util;

/** @deprecated Use {@code common.constants.AppConstants}. Kept for source compatibility. */
@Deprecated(forRemoval = false)
public final class AppConstants {

  private AppConstants() {}

  public static final String ROLE_USER = com.example.chat_app_backend.common.constants.AppConstants.ROLE_USER;
  public static final String ROLE_ADMIN = com.example.chat_app_backend.common.constants.AppConstants.ROLE_ADMIN;
  public static final String ERR_USERNAME_TAKEN = com.example.chat_app_backend.common.constants.AppConstants.ERR_USERNAME_TAKEN;
  public static final String ERR_EMAIL_TAKEN = com.example.chat_app_backend.common.constants.AppConstants.ERR_EMAIL_TAKEN;
  public static final String ERR_ADMIN_EXISTS = com.example.chat_app_backend.common.constants.AppConstants.ERR_ADMIN_EXISTS;
  public static final String ERR_USER_NOT_FOUND = com.example.chat_app_backend.common.constants.AppConstants.ERR_USER_NOT_FOUND;
  public static final String ERR_INVALID_CREDENTIALS = com.example.chat_app_backend.common.constants.AppConstants.ERR_INVALID_CREDENTIALS;
  public static final String MSG_USER_REGISTERED = com.example.chat_app_backend.common.constants.AppConstants.MSG_USER_REGISTERED;
  public static final String MSG_LOGOUT_SUCCESS = com.example.chat_app_backend.common.constants.AppConstants.MSG_LOGOUT_SUCCESS;
}
