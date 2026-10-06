package com.example.chat_app_backend.payload.response;
import com.example.chat_app_backend.model.Report;
import java.util.List;
public class ReportPageResponse extends CursorPageResponse<Report> {
    public ReportPageResponse(List<Report> items, boolean hasMore, String nextCursor) { super(items, hasMore, nextCursor); }
}
