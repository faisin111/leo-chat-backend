package com.example.chat_app_backend.payload.request;

import java.util.List;
import java.util.UUID;

public class CreateChatRequest {
  private String chatName; // Optional
  private String chatType; // ONE_TO_ONE, GROUP
  private List<UUID> participantIds;

  public String getChatName() {
    return chatName;
  }

  public void setChatName(String chatName) {
    this.chatName = chatName;
  }

  public String getChatType() {
    return chatType;
  }

  public void setChatType(String chatType) {
    this.chatType = chatType;
  }

  public List<UUID> getParticipantIds() {
    return participantIds;
  }

  public void setParticipantIds(List<UUID> participantIds) {
    this.participantIds = participantIds;
  }
}
