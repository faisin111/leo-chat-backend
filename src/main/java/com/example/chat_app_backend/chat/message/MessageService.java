package com.example.chat_app_backend.chat.message;

import com.example.chat_app_backend.chat.conversation.ConversationMemberRepository;
import com.example.chat_app_backend.chat.conversation.ConversationRepository;
import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
import com.example.chat_app_backend.exception.ForbiddenException;
import com.example.chat_app_backend.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationMemberRepository memberRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public MessageResponse sendMessage(UUID senderId, SendMessageRequest request) {
        // Idempotency check
        Optional<Message> existing = messageRepository.findByConversationIdAndSenderIdAndClientMessageId(
                request.conversationId(), senderId, request.clientMessageId());
        if (existing.isPresent()) {
            return mapToResponse(existing.get());
        }

        memberRepository.findByIdConversationIdAndIdUserId(request.conversationId(), senderId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this conversation"));

        Instant now = Instant.now();
        conversationRepository.incrementSeqAndTimestamp(request.conversationId(), now);
        
        long nextSeq = conversationRepository.getLastSeq(request.conversationId())
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        Message msg = new Message();
        msg.setConversationId(request.conversationId());
        msg.setSenderId(senderId);
        msg.setSeq(nextSeq);
        msg.setClientMessageId(request.clientMessageId());
        msg.setType(request.type() != null ? MessageType.valueOf(request.type().toUpperCase()) : MessageType.TEXT);
        msg.setContent(request.content());
        msg.setReplyToId(request.replyToId());
        
        msg = messageRepository.save(msg);
        
        // TODO: Publish to Realtime (WebSocket) here
        
        return mapToResponse(msg);
    }
    
    @Transactional(readOnly = true)
    public List<MessageResponse> getHistory(UUID userId, UUID conversationId, long cursor, int limit) {
        memberRepository.findByIdConversationIdAndIdUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this conversation"));
                
        long safeCursor = cursor <= 0 ? Long.MAX_VALUE : cursor;
        List<Message> msgs = messageRepository.findHistory(conversationId, safeCursor);
        return msgs.stream().limit(limit).map(this::mapToResponse).collect(Collectors.toList());
    }

    private MessageResponse mapToResponse(Message m) {
        return new MessageResponse(
                m.getId(), m.getConversationId(), m.getSenderId(), m.getSeq(),
                m.getClientMessageId(), m.getType().name(), m.getContent(), m.getReplyToId(),
                m.getCreatedAt(), m.getEditedAt(), m.getDeletedAt()
        );
    }
}
