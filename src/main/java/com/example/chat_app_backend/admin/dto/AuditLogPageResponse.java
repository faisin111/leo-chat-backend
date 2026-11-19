package com.example.chat_app_backend.admin.dto;

import com.example.chat_app_backend.admin.AuditLog;
import com.example.chat_app_backend.common.dto.CursorPageResponse;
import java.util.List;

public class AuditLogPageResponse extends CursorPageResponse<AuditLog> {
  public AuditLogPageResponse(List<AuditLog> items, boolean hasMore, String nextCursor) {
    super(items, hasMore, nextCursor);
  }
}
