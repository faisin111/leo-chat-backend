package com.example.chat_app_backend.chat.conversation;

import com.example.chat_app_backend.chat.conversation.dto.AddMembersRequest;
import com.example.chat_app_backend.chat.conversation.dto.ChangeRoleRequest;
import com.example.chat_app_backend.chat.conversation.dto.ConversationMemberResponse;
import com.example.chat_app_backend.chat.conversation.dto.ConversationResponse;
import com.example.chat_app_backend.chat.conversation.dto.CreateGroupChatRequest;
import com.example.chat_app_backend.chat.conversation.dto.UnreadCountResponse;
import com.example.chat_app_backend.chat.conversation.dto.UpdateGroupChatRequest;
import com.example.chat_app_backend.common.enums.ConversationMemberRole;
import com.example.chat_app_backend.exception.BusinessRuleException;
import com.example.chat_app_backend.exception.ForbiddenException;
import com.example.chat_app_backend.exception.NotFoundException;
import com.example.chat_app_backend.payload.response.CursorPageResponse;
import com.example.chat_app_backend.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

  @Transactional(readOnly = true)
  public void assertActiveMember(UUID userId, UUID conversationId) {
    memberRepository
        .findByIdConversationIdAndIdUserId(conversationId, userId)
        .filter(member -> member.getLeftAt() == null)
        .orElseThrow(() -> new ForbiddenException("Not a member of this conversation"));
  }

  @Transactional
  public ConversationResponse getOrCreateDirectChat(UUID userId1, UUID userId2) {
    if (userId1.equals(userId2)) {
      throw new BusinessRuleException("Cannot create a conversation with yourself");
    }
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

    return mapToResponse(conv, userId1);
  }

  @Transactional(readOnly = true)
  public CursorPageResponse<ConversationResponse> getUserConversations(
      UUID userId, Long cursor, int limit, String query) {
    if (cursor != null && cursor < 0) {
      throw new BusinessRuleException("Cursor must be zero or greater");
    }
    if (limit < 1 || limit > 100) {
      throw new BusinessRuleException("Limit must be between 1 and 100");
    }
    int page = (cursor == null) ? 0 : cursor.intValue();
    Pageable pageable = PageRequest.of(page, limit);
    String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
    Page<Conversation> pageResult =
        normalizedQuery == null
            ? conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId, pageable)
            : conversationRepository.searchUserConversations(userId, normalizedQuery, pageable);

    List<ConversationResponse> responses =
        pageResult.getContent().stream()
            .map(c -> mapToResponse(c, userId))
            .collect(Collectors.toList());

    boolean hasMore = pageResult.hasNext();
    String nextCursor = hasMore ? String.valueOf(page + 1) : null;
    return new CursorPageResponse<>(responses, hasMore, nextCursor);
  }

  private ConversationResponse mapToResponse(Conversation c, UUID currentUserId) {
    String targetUsername = null;
    String targetDisplayName = null;
    String targetAvatarUrl = null;

    if (c.getType() == ConversationType.DIRECT && c.getDirectKey() != null) {
      String[] parts = c.getDirectKey().split(":");
      if (parts.length == 2) {
        UUID id1 = UUID.fromString(parts[0]);
        UUID id2 = UUID.fromString(parts[1]);
        UUID targetUserId = id1.equals(currentUserId) ? id2 : id1;
        var targetUserOpt = userRepository.findById(targetUserId);
        if (targetUserOpt.isPresent()) {
          var targetUser = targetUserOpt.get();
          targetUsername = targetUser.getUsername();
          targetDisplayName = targetUser.getDisplayName();
          if (targetUser.getProfile() != null) {
            targetAvatarUrl = targetUser.getProfile().getProfilePictureUrl();
          }
        }
      }
    }

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
        c.getStatus(),
        targetUsername,
        targetDisplayName,
        targetAvatarUrl);
  }

  @Transactional(readOnly = true)
  public ConversationResponse getConversation(UUID userId, UUID conversationId) {
    Conversation conv =
        conversationRepository
            .findById(conversationId)
            .orElseThrow(() -> new NotFoundException("Conversation not found"));
    requireActiveMember(conversationId, userId);
    return mapToResponse(conv, userId);
  }

  @Transactional(readOnly = true)
  public UnreadCountResponse getUnreadCount(UUID userId) {
    Long totalUnread = conversationRepository.getUnreadCountByUserId(userId);
    return new UnreadCountResponse(totalUnread != null ? totalUnread : 0L);
  }

  @Transactional
  public ConversationResponse createGroupChat(UUID userId, CreateGroupChatRequest request) {
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

    return mapToResponse(c, userId);
  }

  @Transactional
  public ConversationResponse updateGroupChat(
      UUID userId, UUID id, UpdateGroupChatRequest request) {
    Conversation c =
        conversationRepository.findById(id).orElseThrow(() -> new NotFoundException("Not found"));
    ConversationMember mem = requireActiveMember(id, userId);
    if (!ConversationMemberRole.OWNER.name().equals(mem.getRole())
        && !ConversationMemberRole.ADMIN.name().equals(mem.getRole()))
      throw new ForbiddenException("Only owners and admins can update a group");

    if (request.title() != null) c.setTitle(request.title());
    if (request.avatarUrl() != null) c.setAvatarUrl(request.avatarUrl());
    return mapToResponse(conversationRepository.save(c), userId);
  }

  @Transactional(readOnly = true)
  public List<ConversationMemberResponse> getMembers(UUID userId, UUID id) {
    requireActiveMember(id, userId);
    return memberRepository.findByIdConversationId(id).stream()
        .map(
            m ->
                new ConversationMemberResponse(
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
  public void addMembers(UUID userId, UUID id, AddMembersRequest request) {
    ConversationMember mem = requireActiveMember(id, userId);
    if (!ConversationMemberRole.OWNER.name().equals(mem.getRole())
        && !ConversationMemberRole.ADMIN.name().equals(mem.getRole()))
      throw new ForbiddenException("Only owners and admins can add members");
    for (UUID mId : request.memberIds()) {
      if (userRepository.existsById(mId)) {
        ConversationMemberId memberId = new ConversationMemberId(id, mId);
        ConversationMember existing = memberRepository.findById(memberId).orElse(null);
        if (existing == null) {
          ConversationMember m = new ConversationMember();
          m.setId(memberId);
          memberRepository.save(m);
        } else if (existing.getLeftAt() != null) {
          existing.setLeftAt(null);
          memberRepository.save(existing);
        }
      }
    }
  }

  @Transactional
  public void removeMember(UUID userId, UUID id, UUID targetId) {
    if (!userId.equals(targetId)) {
      ConversationMember mem = requireActiveMember(id, userId);
      if (!ConversationMemberRole.OWNER.name().equals(mem.getRole())
          && !ConversationMemberRole.ADMIN.name().equals(mem.getRole()))
        throw new ForbiddenException("Only owners and admins can remove members");
    }
    ConversationMember target = requireActiveMember(id, targetId);
    target.setLeftAt(Instant.now());
    memberRepository.save(target);
  }

  @Transactional
  public void changeRole(UUID userId, UUID id, UUID targetId, ChangeRoleRequest request) {
    ConversationMember mem = requireActiveMember(id, userId);
    if (!"OWNER".equals(mem.getRole()))
      throw new ForbiddenException("Only the owner can change roles");
    ConversationMember target = requireActiveMember(id, targetId);
    target.setRole(request.role());
    memberRepository.save(target);
  }

  @Transactional
  public void muteConversation(UUID userId, UUID id, Instant mutedUntil) {
    ConversationMember mem = requireActiveMember(id, userId);
    mem.setMutedUntil(mutedUntil);
    memberRepository.save(mem);
  }

  @Transactional
  public void pinConversation(UUID userId, UUID id, boolean pin) {
    ConversationMember mem = requireActiveMember(id, userId);
    mem.setPinnedAt(pin ? Instant.now() : null);
    memberRepository.save(mem);
  }

  @Transactional
  public void archiveConversation(UUID userId, UUID id, boolean archive) {
    ConversationMember mem = requireActiveMember(id, userId);
    mem.setArchivedAt(archive ? Instant.now() : null);
    memberRepository.save(mem);
  }

  private ConversationMember requireActiveMember(UUID conversationId, UUID userId) {
    return memberRepository
        .findById(new ConversationMemberId(conversationId, userId))
        .filter(member -> member.getLeftAt() == null)
        .orElseThrow(() -> new ForbiddenException("Not a member of this conversation"));
  }
}
