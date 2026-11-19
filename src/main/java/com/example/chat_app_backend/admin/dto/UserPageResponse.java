package com.example.chat_app_backend.admin.dto;

import com.example.chat_app_backend.common.dto.CursorPageResponse;
import com.example.chat_app_backend.user.User;
import java.util.List;

public class UserPageResponse extends CursorPageResponse<User> {
  public UserPageResponse(List<User> items, boolean hasMore, String nextCursor) {
    super(items, hasMore, nextCursor);
  }
}
