package com.example.chat_app_backend.payload.request;

import java.util.UUID;

public class ChatMessagePayload {
    private UUID chatId;
    private UUID senderId;
    private String content;
    private String messageType; // TEXT, IMAGE

    public UUID getChatId() { return chatId; }
    public void setChatId(UUID chatId) { this.chatId = chatId; }

    public UUID getSenderId() { return senderId; }
    public void setSenderId(UUID senderId) { this.senderId = senderId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
}
