package com.example.chat_app_backend.chat.message.dto;

import com.example.chat_app_backend.common.dto.CursorPageResponse;
import java.util.List;

public class MessagePageResponse extends CursorPageResponse<MessageResponse> {
  public MessagePageResponse(List<MessageResponse> items, boolean hasMore, String nextCursor) {
    super(items, hasMore, nextCursor);
  }
}
