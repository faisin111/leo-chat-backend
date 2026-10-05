package com.example.chat_app_backend.payload.response;

import java.util.List;

public record CursorPageResponse<T>(List<T> items, boolean hasMore, String nextCursor) {}
