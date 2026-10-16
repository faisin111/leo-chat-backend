package com.example.chat_app_backend.chat.message;

import com.example.chat_app_backend.chat.conversation.ConversationMember;
import com.example.chat_app_backend.chat.conversation.ConversationMemberRepository;
import com.example.chat_app_backend.chat.conversation.ConversationRepository;
import com.example.chat_app_backend.chat.conversation.ConversationService;
import com.example.chat_app_backend.chat.message.dto.AddReactionRequest;
import com.example.chat_app_backend.chat.message.dto.EditMessageRequest;
import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.chat.message.dto.MessageReactionResponse;
import com.example.chat_app_backend.chat.message.dto.MessageReactionUserResponse;
import com.example.chat_app_backend.chat.message.dto.MessageReplyResponse;
import com.example.chat_app_backend.chat.message.dto.ReadMessageRequest;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
import com.example.chat_app_backend.common.enums.ConversationMemberRole;
import com.example.chat_app_backend.exception.BusinessRuleException;
import com.example.chat_app_backend.exception.ForbiddenException;
import com.example.chat_app_backend.exception.NotFoundException;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.payload.response.CursorPageResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MessageService {

  private final MessageRepository messageRepository;
  private final ConversationRepository conversationRepository;
  private final ConversationMemberRepository memberRepository;
  private final SimpMessagingTemplate messagingTemplate;
  private final ConversationService conversationService;
  private final MessageReactionRepository reactionRepository;
  private final UserRepository userRepository;

  public MessageService(
      MessageRepository messageRepository,
      ConversationRepository conversationRepository,
      ConversationMemberRepository memberRepository,
      SimpMessagingTemplate messagingTemplate,
      ConversationService conversationService,
      MessageReactionRepository reactionRepository,
      UserRepository userRepository) {
    this.messageRepository = messageRepository;
    this.conversationRepository = conversationRepository;
    this.memberRepository = memberRepository;
    this.messagingTemplate = messagingTemplate;
    this.conversationService = conversationService;
    this.reactionRepository = reactionRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public MessageResponse sendMessage(UUID senderId, UUID convId, SendMessageRequest request) {
    return sendMessage(senderId, convId, request, null);
  }

  @Transactional
  public MessageResponse sendMessage(
      UUID senderId, UUID convId, SendMessageRequest request, String ackUsername) {
    conversationService.assertActiveMember(senderId, convId);
    if (request.conversationId() != null && !convId.equals(request.conversationId())) {
      throw new BusinessRuleException("Conversation ID does not match path");
    }
    MessageType type;
    try {
      type =
          request.type() == null
              ? MessageType.TEXT
              : MessageType.valueOf(request.type().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BusinessRuleException("Invalid message type");
    }
    if (type == MessageType.SYSTEM) throw new ForbiddenException("System messages are server-only");
    if (request.clientMessageId() == null
        || request.clientMessageId().isBlank()
        || request.clientMessageId().length() > 64
        || request.content() == null
        || request.content().isBlank()
        || request.content().length() > 4000) {
      throw new BusinessRuleException("Invalid message content or clientMessageId");
    }
    // Serialize retries and sequence allocation before looking up the idempotency key.
    conversationRepository
        .lockById(convId)
        .orElseThrow(() -> new NotFoundException("Conversation not found"));
    Optional<Message> existing =
        messageRepository.findByConversationIdAndSenderIdAndClientMessageId(
            convId, senderId, request.clientMessageId());
    if (existing.isPresent()) {
      MessageResponse response = mapToResponse(existing.get());
      if (ackUsername != null)
        afterCommit(
            () -> messagingTemplate.convertAndSendToUser(ackUsername, "/queue/acks", response));
      return response;
    }

    Instant now = Instant.now();
    conversationRepository.incrementSeqAndTimestamp(convId, now);

    long nextSeq =
        conversationRepository
            .getLastSeq(convId)
            .orElseThrow(() -> new NotFoundException("Conversation not found"));

    Message msg = new Message();
    msg.setConversationId(convId);
    msg.setSenderId(senderId);
    msg.setSeq(nextSeq);
    msg.setClientMessageId(request.clientMessageId());
    if (request.replyToId() != null) {
      Message parent =
          messageRepository
              .findById(request.replyToId())
              .orElseThrow(() -> new NotFoundException("Reply message not found"));
      if (!convId.equals(parent.getConversationId()))
        throw new BusinessRuleException("Reply belongs to another conversation");
    }
    msg.setType(type);
    msg.setContent(request.content().strip());
    msg.setCreatedAt(now);
    msg.setReplyToId(request.replyToId());

    msg = messageRepository.saveAndFlush(msg);
    MessageResponse response = mapToResponse(msg);
    if (ackUsername != null)
      afterCommit(
          () -> messagingTemplate.convertAndSendToUser(ackUsername, "/queue/acks", response));
    broadcastAfterCommit(convId, response);
    return response;
  }

  @Transactional(readOnly = true)
  public CursorPageResponse<MessageResponse> getHistory(
      UUID userId, UUID convId, Long beforeSeq, Long afterSeq, int limit) {
    return getHistory(userId, convId, beforeSeq, afterSeq, limit, null);
  }

  @Transactional(readOnly = true)
  public CursorPageResponse<MessageResponse> getHistory(
      UUID userId, UUID convId, Long beforeSeq, Long afterSeq, int limit, String query) {
    conversationService.assertActiveMember(userId, convId);
    if (limit < 1
        || limit > 100
        || (beforeSeq != null && afterSeq != null)
        || (beforeSeq != null && beforeSeq <= 0)
        || (afterSeq != null && afterSeq < 0)) {
      throw new BusinessRuleException("Invalid history cursor or limit");
    }
    long safeCursor = (beforeSeq == null || beforeSeq <= 0) ? Long.MAX_VALUE : beforeSeq;
    Pageable page = PageRequest.of(0, limit + 1);
    List<Message> msgs =
        query != null && !query.isBlank()
            ? messageRepository.searchContent(convId, query.strip(), page)
            : afterSeq == null
                ? messageRepository.findHistory(convId, safeCursor, page)
                : messageRepository.findAfter(convId, afterSeq, page);
    List<MessageResponse> responses =
        msgs.stream().limit(limit).map(this::mapToResponse).collect(Collectors.toList());
    boolean hasMore = msgs.size() > limit;
    String nextCursor =
        responses.isEmpty() ? null : String.valueOf(responses.get(responses.size() - 1).seq());
    return new CursorPageResponse<>(responses, hasMore, nextCursor);
  }

  @Transactional
  public void readMessages(UUID userId, UUID convId, ReadMessageRequest request) {
    conversationService.assertActiveMember(userId, convId);
    long lastSeq =
        conversationRepository
            .getLastSeq(convId)
            .orElseThrow(() -> new NotFoundException("Conversation not found"));
    if (request.upToSeq() < 0 || request.upToSeq() > lastSeq)
      throw new BusinessRuleException("Read sequence out of range");
    memberRepository.advanceReadSeq(convId, userId, request.upToSeq());
  }

  @Transactional(readOnly = true)
  public MessageResponse getMessage(UUID userId, UUID id) {
    Message m =
        messageRepository.findById(id).orElseThrow(() -> new NotFoundException("Not found"));
    conversationService.assertActiveMember(userId, m.getConversationId());
    return mapToResponse(m);
  }

  @Transactional
  public MessageResponse editMessage(UUID userId, UUID id, EditMessageRequest request) {
    Message m =
        messageRepository.lockById(id).orElseThrow(() -> new NotFoundException("Not found"));
    conversationService.assertActiveMember(userId, m.getConversationId());
    if (m.getDeletedAt() != null) throw new BusinessRuleException("Message is deleted");
    if (!m.getSenderId().equals(userId)) throw new ForbiddenException("Not sender");

    if (m.getCreatedAt().plus(Duration.ofMinutes(15)).isBefore(Instant.now())) {
      throw new BusinessRuleException("Edit window expired");
    }

    m.setContent(request.content());
    m.setEditedAt(Instant.now());
    MessageResponse response = mapToResponse(messageRepository.save(m));
    broadcastAfterCommit(m.getConversationId(), response);
    return response;
  }

  @Transactional
  public void deleteMessage(UUID userId, UUID id) {
    Message m =
        messageRepository.lockById(id).orElseThrow(() -> new NotFoundException("Not found"));
    conversationService.assertActiveMember(userId, m.getConversationId());
    ConversationMember mem =
        memberRepository
            .findByIdConversationIdAndIdUserId(m.getConversationId(), userId)
            .orElseThrow(() -> new ForbiddenException("Not a member"));

    if (!m.getSenderId().equals(userId)
        && !"OWNER".equals(mem.getRole())
        && !ConversationMemberRole.ADMIN.name().equals(mem.getRole())) {
      throw new ForbiddenException("Not authorized");
    }

    m.setDeletedAt(Instant.now());
    messageRepository.save(m);
    broadcastAfterCommit(m.getConversationId(), mapToResponse(m));
  }

  @Transactional
  public void addReaction(UUID userId, UUID id, AddReactionRequest request) {
    Message message =
        messageRepository.lockById(id).orElseThrow(() -> new NotFoundException("Not found"));
    conversationService.assertActiveMember(userId, message.getConversationId());
    if (message.getDeletedAt() != null) throw new BusinessRuleException("Message is deleted");
    if (request.emoji() == null || request.emoji().isBlank() || request.emoji().length() > 16)
      throw new BusinessRuleException("Invalid emoji");
    MessageReaction reaction = new MessageReaction();
    reaction.setMessageId(id);
    reaction.setUserId(userId);
    reaction.setEmoji(request.emoji());
    reactionRepository.save(reaction);
    broadcastAfterCommit(message.getConversationId(), mapToResponse(message));
  }

  @Transactional
  public void removeReaction(UUID userId, UUID id, String emoji) {
    Message message =
        messageRepository.lockById(id).orElseThrow(() -> new NotFoundException("Not found"));
    conversationService.assertActiveMember(userId, message.getConversationId());
    reactionRepository.deleteById(new MessageReaction.MessageReactionId(id, userId, emoji));
    broadcastAfterCommit(message.getConversationId(), mapToResponse(message));
  }

  private void broadcastAfterCommit(UUID conversationId, MessageResponse response) {
    afterCommit(
        () -> messagingTemplate.convertAndSend("/topic/conversations." + conversationId, response));
  }

  private void afterCommit(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  private MessageResponse mapToResponse(Message m) {
    User sender = userRepository.findById(m.getSenderId()).orElse(null);
    MessageReplyResponse reply = null;
    if (m.getReplyToId() != null) {
      Message parent = messageRepository.findById(m.getReplyToId()).orElse(null);
      if (parent != null) {
        User parentSender = userRepository.findById(parent.getSenderId()).orElse(null);
        reply =
            new MessageReplyResponse(
                parent.getId(),
                parent.getSenderId(),
                parentSender == null ? null : parentSender.getUsername(),
                parentSender == null ? null : parentSender.getDisplayName(),
                parent.getDeletedAt() == null ? parent.getContent() : null,
                parent.getDeletedAt() != null);
      }
    }
    return new MessageResponse(
        m.getId(),
        m.getConversationId(),
        m.getSenderId(),
        sender == null ? null : sender.getUsername(),
        sender == null ? null : sender.getDisplayName(),
        m.getSeq(),
        m.getClientMessageId(),
        m.getType().name(),
        m.getDeletedAt() == null ? m.getContent() : null,
        m.getReplyToId(),
        reply,
        mapReactions(m.getId()),
        m.getCreatedAt(),
        m.getEditedAt(),
        m.getDeletedAt(),
        m.getDeletedAt() != null);
  }

  private List<MessageReactionResponse> mapReactions(UUID messageId) {
    Map<String, List<MessageReactionUserResponse>> grouped = new LinkedHashMap<>();
    for (MessageReaction reaction : reactionRepository.findByMessageId(messageId)) {
      User user = userRepository.findById(reaction.getUserId()).orElse(null);
      grouped
          .computeIfAbsent(reaction.getEmoji(), ignored -> new ArrayList<>())
          .add(
              new MessageReactionUserResponse(
                  reaction.getUserId(),
                  user == null ? null : user.getUsername(),
                  user == null ? null : user.getDisplayName()));
    }
    return grouped.entrySet().stream()
        .map(e -> new MessageReactionResponse(e.getKey(), e.getValue().size(), e.getValue()))
        .collect(Collectors.toList());
  }
}
