package com.example.chat_app_backend.chat.conversation.dto;

import com.example.chat_app_backend.common.dto.CursorPageResponse;
import java.util.List;

public class ConversationPageResponse extends CursorPageResponse<ConversationResponse> {
  public ConversationPageResponse(
      List<ConversationResponse> items, boolean hasMore, String nextCursor) {
    super(items, hasMore, nextCursor);
  }
}
