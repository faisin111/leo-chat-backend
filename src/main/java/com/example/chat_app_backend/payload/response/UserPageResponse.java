package com.example.chat_app_backend.payload.response;
import com.example.chat_app_backend.model.User;
import java.util.List;
public class UserPageResponse extends CursorPageResponse<User> {
    public UserPageResponse(List<User> items, boolean hasMore, String nextCursor) { super(items, hasMore, nextCursor); }
}
