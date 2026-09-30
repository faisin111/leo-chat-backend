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

    
    @org.springframework.transaction.annotation.Transactional
    public MessageResponse sendMessage(UUID senderId, UUID convId, SendMessageRequest request) {
        Optional<Message> existing = messageRepository.findByConversationIdAndSenderIdAndClientMessageId(
                convId, senderId, request.clientMessageId());
        if (existing.isPresent()) {
            return mapToResponse(existing.get());
        }

        com.example.chat_app_backend.chat.conversation.ConversationMember mem = memberRepository.findByIdConversationIdAndIdUserId(convId, senderId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this conversation"));

        Instant now = Instant.now();
        conversationRepository.incrementSeqAndTimestamp(convId, now);
        
        long nextSeq = conversationRepository.getLastSeq(convId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        Message msg = new Message();
        msg.setConversationId(convId);
        msg.setSenderId(senderId);
        msg.setSeq(nextSeq);
        msg.setClientMessageId(request.clientMessageId());
        msg.setType(request.type() != null ? MessageType.valueOf(request.type().toUpperCase()) : MessageType.TEXT);
        msg.setContent(request.content());
        msg.setReplyToId(request.replyToId());
        
        msg = messageRepository.save(msg);
        return mapToResponse(msg);
    }
    
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MessageResponse> getHistory(UUID userId, UUID convId, Long beforeSeq, Long afterSeq, int limit) {
        memberRepository.findByIdConversationIdAndIdUserId(convId, userId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this conversation"));
                
        long safeCursor = (beforeSeq == null || beforeSeq <= 0) ? Long.MAX_VALUE : beforeSeq;
        List<Message> msgs = messageRepository.findHistory(convId, safeCursor);
        return msgs.stream().limit(limit).map(this::mapToResponse).collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional
    public void readMessages(UUID userId, UUID convId, com.example.chat_app_backend.chat.message.dto.ReadMessageRequest request) {
        com.example.chat_app_backend.chat.conversation.ConversationMember mem = memberRepository.findByIdConversationIdAndIdUserId(convId, userId)
                .orElseThrow(() -> new ForbiddenException("Not a member"));
        mem.setLastReadSeq(Math.max(mem.getLastReadSeq(), request.upToSeq()));
        memberRepository.save(mem);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public MessageResponse getMessage(UUID userId, UUID id) {
        Message m = messageRepository.findById(id).orElseThrow(() -> new NotFoundException("Not found"));
        memberRepository.findByIdConversationIdAndIdUserId(m.getConversationId(), userId)
                .orElseThrow(() -> new ForbiddenException("Not a member"));
        return mapToResponse(m);
    }

    @org.springframework.transaction.annotation.Transactional
    public MessageResponse editMessage(UUID userId, UUID id, com.example.chat_app_backend.chat.message.dto.EditMessageRequest request) {
        Message m = messageRepository.findById(id).orElseThrow(() -> new NotFoundException("Not found"));
        if (!m.getSenderId().equals(userId)) throw new ForbiddenException("Not sender");
        
        if (m.getCreatedAt().plus(java.time.Duration.ofMinutes(15)).isBefore(Instant.now())) {
            throw new RuntimeException("Edit window expired");
        }
        
        m.setContent(request.content());
        m.setEditedAt(Instant.now());
        return mapToResponse(messageRepository.save(m));
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteMessage(UUID userId, UUID id) {
        Message m = messageRepository.findById(id).orElseThrow(() -> new NotFoundException("Not found"));
        com.example.chat_app_backend.chat.conversation.ConversationMember mem = memberRepository.findByIdConversationIdAndIdUserId(m.getConversationId(), userId)
                .orElseThrow(() -> new ForbiddenException("Not a member"));
        
        if (!m.getSenderId().equals(userId) && !"OWNER".equals(mem.getRole()) && !"ADMIN".equals(mem.getRole())) {
            throw new ForbiddenException("Not authorized");
        }
        
        m.setDeletedAt(Instant.now());
        messageRepository.save(m);
    }

    @org.springframework.transaction.annotation.Transactional
    public void addReaction(UUID userId, UUID id, com.example.chat_app_backend.chat.message.dto.AddReactionRequest request) {
        System.out.println("Reaction added: " + request.emoji());
    }

    @org.springframework.transaction.annotation.Transactional
    public void removeReaction(UUID userId, UUID id, String emoji) {
        System.out.println("Reaction removed: " + emoji);
    }

    private MessageResponse mapToResponse(Message m) {
        return new MessageResponse(
                m.getId(), m.getConversationId(), m.getSenderId(), m.getSeq(),
                m.getClientMessageId(), m.getType().name(), m.getContent(), m.getReplyToId(),
                m.getCreatedAt(), m.getEditedAt(), m.getDeletedAt()
        );
    }
}
