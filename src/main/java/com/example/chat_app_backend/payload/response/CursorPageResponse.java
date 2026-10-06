package com.example.chat_app_backend.payload.response;

import java.util.List;

public class CursorPageResponse<T> {
    private List<T> items;
    private boolean hasMore;
    private String nextCursor;

    public CursorPageResponse(List<T> items, boolean hasMore, String nextCursor) {
        this.items = items;
        this.hasMore = hasMore;
        this.nextCursor = nextCursor;
    }

    public List<T> getItems() { return items; }
    public boolean isHasMore() { return hasMore; }
    public String getNextCursor() { return nextCursor; }
}
