package com.example.chat_app_backend.chat.conversation;

import com.example.chat_app_backend.chat.conversation.dto.ConversationResponse;
import com.example.chat_app_backend.exception.NotFoundException;
import com.example.chat_app_backend.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

  private final ConversationRepository conversationRepository;
  private final ConversationMemberRepository memberRepository;
  private final UserRepository userRepository;

  public ConversationService(
      ConversationRepository conversationRepository,
      ConversationMemberRepository memberRepository,
      UserRepository userRepository) {
    this.conversationRepository = conversationRepository;
    this.memberRepository = memberRepository;
    this.userRepository = userRepository;
  }

  @Transactional
  public ConversationResponse getOrCreateDirectChat(UUID userId1, UUID userId2) {
    if (!userRepository.existsById(userId2)) {
      throw new NotFoundException("Target user not found");
    }

    String key = userId1.compareTo(userId2) < 0 ? userId1 + ":" + userId2 : userId2 + ":" + userId1;

    Conversation conv =
        conversationRepository
            .findByDirectKey(key)
            .orElseGet(
                () -> {
                  Conversation c = new Conversation();
                  c.setType(ConversationType.DIRECT);
                  c.setDirectKey(key);
                  c.setCreatedBy(userId1);
                  c = conversationRepository.save(c);

                  ConversationMember m1 = new ConversationMember();
                  m1.setId(new ConversationMemberId(c.getId(), userId1));

                  ConversationMember m2 = new ConversationMember();
                  m2.setId(new ConversationMemberId(c.getId(), userId2));

                  memberRepository.save(m1);
                  memberRepository.save(m2);

                  return c;
                });

    return mapToResponse(conv);
  }

  @Transactional(readOnly = true)
  public com.example.chat_app_backend.payload.response.CursorPageResponse<ConversationResponse>
      getUserConversations(UUID userId, Long cursor, int limit) {
    int page = (cursor == null) ? 0 : cursor.intValue();
    org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, limit);
    org.springframework.data.domain.Page<Conversation> pageResult = conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId, pageable);

    List<ConversationResponse> responses = pageResult.getContent().stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());

    boolean hasMore = pageResult.hasNext();
    String nextCursor = hasMore ? String.valueOf(page + 1) : null;
    return new com.example.chat_app_backend.payload.response.CursorPageResponse<>(
        responses, hasMore, nextCursor);
  }

  private ConversationResponse mapToResponse(Conversation c) {
    return new ConversationResponse(
        c.getId(),
        c.getType().name(),
        c.getTitle(),
        c.getAvatarUrl(),
        c.getDirectKey(),
        c.getCreatedBy(),
        c.getLastSeq(),
        c.getLastMessageAt(),
        c.getCreatedAt(),
        c.getStatus());
  }

  @Transactional(readOnly = true)
  public ConversationResponse getConversation(UUID userId, UUID conversationId) {
    Conversation conv =
        conversationRepository
            .findById(conversationId)
            .orElseThrow(() -> new NotFoundException("Conversation not found"));
    // check membership
    memberRepository
        .findById(new ConversationMemberId(conversationId, userId))
        .orElseThrow(() -> new RuntimeException("Not a member"));
    return mapToResponse(conv);
  }

  @Transactional(readOnly = true)
  public com.example.chat_app_backend.chat.conversation.dto.UnreadCountResponse getUnreadCount(
      UUID userId) {
    Long totalUnread = conversationRepository.getUnreadCountByUserId(userId);
    return new com.example.chat_app_backend.chat.conversation.dto.UnreadCountResponse(totalUnread != null ? totalUnread : 0L);
  }

  @Transactional
  public ConversationResponse createGroupChat(
      UUID userId,
      com.example.chat_app_backend.chat.conversation.dto.CreateGroupChatRequest request) {
    Conversation c = new Conversation();
    c.setType(ConversationType.GROUP);
    c.setTitle(request.title());
    c.setCreatedBy(userId);
    c = conversationRepository.save(c);

    ConversationMember m = new ConversationMember();
    m.setId(new ConversationMemberId(c.getId(), userId));
    m.setRole("OWNER");
    memberRepository.save(m);

    for (UUID memberId : request.memberIds()) {
      if (memberId.equals(userId)) continue;
      if (!userRepository.existsById(memberId)) continue;
      ConversationMember m2 = new ConversationMember();
      m2.setId(new ConversationMemberId(c.getId(), memberId));
      m2.setRole("MEMBER");
      memberRepository.save(m2);
    }

    return mapToResponse(c);
  }

  @Transactional
  public ConversationResponse updateGroupChat(
      UUID userId,
      UUID id,
      com.example.chat_app_backend.chat.conversation.dto.UpdateGroupChatRequest request) {
    Conversation c =
        conversationRepository.findById(id).orElseThrow(() -> new NotFoundException("Not found"));
    ConversationMember mem =
        memberRepository
            .findById(new ConversationMemberId(id, userId))
            .orElseThrow(() -> new RuntimeException("Not member"));
    if (!"OWNER".equals(mem.getRole()) && !"ADMIN".equals(mem.getRole()))
      throw new RuntimeException("Forbidden");

    if (request.title() != null) c.setTitle(request.title());
    if (request.avatarUrl() != null) c.setAvatarUrl(request.avatarUrl());
    return mapToResponse(conversationRepository.save(c));
  }

  @Transactional(readOnly = true)
  public List<com.example.chat_app_backend.chat.conversation.dto.ConversationMemberResponse>
      getMembers(UUID userId, UUID id) {
    memberRepository
        .findById(new ConversationMemberId(id, userId))
        .orElseThrow(() -> new RuntimeException("Not member"));
    return memberRepository.findByIdConversationId(id).stream()
        .map(
            m ->
                new com.example.chat_app_backend.chat.conversation.dto.ConversationMemberResponse(
                    m.getId().getConversationId(),
                    m.getId().getUserId(),
                    m.getRole(),
                    m.getLastReadSeq(),
                    m.getMutedUntil(),
                    m.getJoinedAt(),
                    m.getLeftAt(),
                    m.getPinnedAt(),
                    m.getArchivedAt()))
        .collect(Collectors.toList());
  }

  @Transactional
  public void addMembers(
      UUID userId,
      UUID id,
      com.example.chat_app_backend.chat.conversation.dto.AddMembersRequest request) {
    ConversationMember mem =
        memberRepository
            .findById(new ConversationMemberId(id, userId))
            .orElseThrow(() -> new RuntimeException("Not member"));
    if (!"OWNER".equals(mem.getRole()) && !"ADMIN".equals(mem.getRole()))
      throw new RuntimeException("Forbidden");
    for (UUID mId : request.memberIds()) {
      if (userRepository.existsById(mId)) {
        ConversationMember m = new ConversationMember();
        m.setId(new ConversationMemberId(id, mId));
        memberRepository.save(m);
      }
    }
  }

  @Transactional
  public void removeMember(UUID userId, UUID id, UUID targetId) {
    if (!userId.equals(targetId)) {
      ConversationMember mem =
          memberRepository
              .findById(new ConversationMemberId(id, userId))
              .orElseThrow(() -> new RuntimeException("Not member"));
      if (!"OWNER".equals(mem.getRole()) && !"ADMIN".equals(mem.getRole()))
        throw new RuntimeException("Forbidden");
    }
    memberRepository.deleteById(new ConversationMemberId(id, targetId));
  }

  @Transactional
  public void changeRole(
      UUID userId,
      UUID id,
      UUID targetId,
      com.example.chat_app_backend.chat.conversation.dto.ChangeRoleRequest request) {
    ConversationMember mem =
        memberRepository
            .findById(new ConversationMemberId(id, userId))
            .orElseThrow(() -> new RuntimeException("Not member"));
    if (!"OWNER".equals(mem.getRole())) throw new RuntimeException("Forbidden");
    ConversationMember target =
        memberRepository
            .findById(new ConversationMemberId(id, targetId))
            .orElseThrow(() -> new RuntimeException("Not member"));
    target.setRole(request.role());
    memberRepository.save(target);
  }

  @Transactional
  public void muteConversation(UUID userId, UUID id, java.time.Instant mutedUntil) {
    ConversationMember mem =
        memberRepository
            .findById(new ConversationMemberId(id, userId))
            .orElseThrow(() -> new RuntimeException("Not member"));
    mem.setMutedUntil(mutedUntil);
    memberRepository.save(mem);
  }

  @Transactional
  public void pinConversation(UUID userId, UUID id, boolean pin) {
    ConversationMember mem =
        memberRepository
            .findById(new ConversationMemberId(id, userId))
            .orElseThrow(() -> new RuntimeException("Not member"));
    mem.setPinnedAt(pin ? java.time.Instant.now() : null);
    memberRepository.save(mem);
  }

  @Transactional
  public void archiveConversation(UUID userId, UUID id, boolean archive) {
    ConversationMember mem =
        memberRepository
            .findById(new ConversationMemberId(id, userId))
            .orElseThrow(() -> new RuntimeException("Not member"));
    mem.setArchivedAt(archive ? java.time.Instant.now() : null);
    memberRepository.save(mem);
  }
}
