package com.example.chat_app_backend.payload.response;
import com.example.chat_app_backend.model.AuditLog;
import java.util.List;
public class AuditLogPageResponse extends CursorPageResponse<AuditLog> {
    public AuditLogPageResponse(List<AuditLog> items, boolean hasMore, String nextCursor) { super(items, hasMore, nextCursor); }
}
