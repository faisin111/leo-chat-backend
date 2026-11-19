package com.example.chat_app_backend.admin.dto;

import com.example.chat_app_backend.admin.Report;
import com.example.chat_app_backend.common.dto.CursorPageResponse;
import java.util.List;

public class ReportPageResponse extends CursorPageResponse<Report> {
  public ReportPageResponse(List<Report> items, boolean hasMore, String nextCursor) {
    super(items, hasMore, nextCursor);
  }
}
