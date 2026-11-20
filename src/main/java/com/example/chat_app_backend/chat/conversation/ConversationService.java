package com.example.chat_app_backend.chat.conversation;

import com.example.chat_app_backend.chat.conversation.dto.AddMembersRequest;
import com.example.chat_app_backend.chat.conversation.dto.ChangeRoleRequest;
import com.example.chat_app_backend.chat.conversation.dto.ConversationMemberResponse;
import com.example.chat_app_backend.chat.conversation.dto.ConversationResponse;
import com.example.chat_app_backend.chat.conversation.dto.CreateGroupChatRequest;
import com.example.chat_app_backend.chat.conversation.dto.UnreadCountResponse;
import com.example.chat_app_backend.chat.conversation.dto.UpdateGroupChatRequest;
import com.example.chat_app_backend.chat.message.MessageAttachmentRepository;
import com.example.chat_app_backend.chat.message.MessageReactionRepository;
import com.example.chat_app_backend.chat.message.MessageRepository;
import com.example.chat_app_backend.common.dto.CursorPageResponse;
import com.example.chat_app_backend.common.enums.ConversationMemberRole;
import com.example.chat_app_backend.common.enums.UserStatus;
import com.example.chat_app_backend.common.exception.BusinessRuleException;
import com.example.chat_app_backend.common.exception.ForbiddenException;
import com.example.chat_app_backend.common.exception.NotFoundException;
import com.example.chat_app_backend.media.MediaService;
import com.example.chat_app_backend.user.UserRepository;
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
  private final MessageRepository messageRepository;
  private final MessageAttachmentRepository attachmentRepository;
  private final MessageReactionRepository reactionRepository;
  private final MediaService mediaService;

  public ConversationService(
      ConversationRepository conversationRepository,
      ConversationMemberRepository memberRepository,
      UserRepository userRepository,
      MessageRepository messageRepository,
      MessageAttachmentRepository attachmentRepository,
      MessageReactionRepository reactionRepository,
      MediaService mediaService) {
    this.conversationRepository = conversationRepository;
    this.memberRepository = memberRepository;
    this.userRepository = userRepository;
    this.messageRepository = messageRepository;
    this.attachmentRepository = attachmentRepository;
    this.reactionRepository = reactionRepository;
    this.mediaService = mediaService;
  }

  @Transactional(readOnly = true)
  public void assertActiveMember(UUID userId, UUID conversationId) {
    memberRepository
        .findByIdConversationIdAndIdUserId(conversationId, userId)
        .filter(member -> member.getLeftAt() == null)
        .orElseThrow(() -> new ForbiddenException("Not a member of this conversation"));
  }

  /**
   * Resolves the conversation an AI request runs in.
   *
   * <ul>
   *   <li>{@code null}: the user's default personal AI conversation (created on first use).
   *   <li>An existing normal chat: the in-chat assistant; the user must be an active member.
   *   <li>An existing AI conversation: must be owned by the user.
   *   <li>An unknown UUID: a new AI conversation owned by the user (one per client chat).
   * </ul>
   */
  @Transactional
  public UUID ensureAiConversation(UUID userId, UUID requestedConversationId) {
    if (requestedConversationId == null) return ensureDefaultAiConversation(userId);

    var existing = conversationRepository.findById(requestedConversationId);
    if (existing.isPresent()) {
      Conversation conversation = existing.get();
      if (conversation.getType() != ConversationType.AI) {
        assertActiveMember(userId, conversation.getId());
        return conversation.getId();
      }
      requireAiOwner(userId, conversation);
      ensureAiMember(userId, conversation);
      return conversation.getId();
    }

    conversationRepository.insertAiConversationIfAbsent(
        requestedConversationId, "ai:" + userId + ":" + requestedConversationId, userId);
    Conversation conversation =
        conversationRepository
            .findById(requestedConversationId)
            .orElseThrow(() -> new NotFoundException("AI conversation could not be created"));
    if (conversation.getType() != ConversationType.AI) {
      assertActiveMember(userId, conversation.getId());
      return conversation.getId();
    }
    requireAiOwner(userId, conversation);
    ensureAiMember(userId, conversation);
    return conversation.getId();
  }

  private static void requireAiOwner(UUID userId, Conversation conversation) {
    if (!userId.equals(conversation.getCreatedBy())) {
      throw new ForbiddenException("Not a member of this conversation");
    }
  }

  private UUID ensureDefaultAiConversation(UUID userId) {
    String directKey = "ai:" + userId;
    var existing = conversationRepository.findByDirectKey(directKey);
    if (existing.isPresent()) {
      Conversation conversation = existing.get();
      ensureAiMember(userId, conversation);
      return conversation.getId();
    }

    var legacy = conversationRepository.findLegacyAiConversations(userId);
    if (!legacy.isEmpty()) {
      Conversation conversation = legacy.get(0);
      conversation.setType(ConversationType.AI);
      conversation.setDirectKey(directKey);
      conversationRepository.save(conversation);
      return conversation.getId();
    }

    UUID requestedId = UUID.randomUUID();
    conversationRepository.insertAiConversationIfAbsent(requestedId, directKey, userId);
    Conversation conversation =
        conversationRepository
            .findByDirectKey(directKey)
            .orElseThrow(() -> new NotFoundException("AI conversation could not be created"));
    ensureAiMember(userId, conversation);
    return conversation.getId();
  }

  private void ensureAiMember(UUID userId, Conversation conversation) {
    ConversationMemberId memberId = new ConversationMemberId(conversation.getId(), userId);
    ConversationMember member = memberRepository.findById(memberId).orElse(null);
    if (member == null) {
      member = new ConversationMember();
      member.setId(memberId);
      member.setRole("OWNER");
      memberRepository.save(member);
    } else if (member.getLeftAt() != null) {
      member.setLeftAt(null);
      memberRepository.save(member);
    }
  }

  @Transactional
  public ConversationResponse getOrCreateAiConversation(UUID userId) {
    UUID id = ensureAiConversation(userId, null);
    return getConversation(userId, id);
  }

  @Transactional
  public void deleteConversationOrLeave(UUID userId, UUID conversationId) {
    Conversation conversation =
        conversationRepository
            .findById(conversationId)
            .orElseThrow(() -> new NotFoundException("Conversation not found"));
    requireActiveMember(conversationId, userId);
    boolean aiConversation = conversation.getType() == ConversationType.AI;
    if (!aiConversation) {
      ConversationMember member = requireActiveMember(conversationId, userId);
      member.setLeftAt(Instant.now());
      memberRepository.save(member);
      if (memberRepository.countByIdConversationIdAndLeftAtIsNull(conversationId) > 0) {
        if (ConversationMemberRole.OWNER.name().equals(member.getRole())) {
          promoteNextOwner(conversationId);
        }
        return;
      }
    }
    reactionRepository.deleteByConversationId(conversationId);
    attachmentRepository.deleteByConversationId(conversationId);
    messageRepository.deleteByConversationId(conversationId);
    memberRepository.deleteByIdConversationId(conversationId);
    conversationRepository.delete(conversation);
  }

  @Transactional
  public void deleteAiConversation(UUID userId) {
    conversationRepository
        .findByDirectKey("ai:" + userId)
        .ifPresent(conversation -> deleteConversationOrLeave(userId, conversation.getId()));
  }

  /** Permanently deletes one of the user's AI conversations. Normal chats are rejected. */
  @Transactional
  public void deleteAiConversation(UUID userId, UUID conversationId) {
    Conversation conversation =
        conversationRepository
            .findById(conversationId)
            .orElseThrow(() -> new NotFoundException("Conversation not found"));
    if (conversation.getType() != ConversationType.AI) {
      throw new BusinessRuleException("Not an AI conversation");
    }
    requireAiOwner(userId, conversation);
    deleteConversationOrLeave(userId, conversationId);
  }

  /** The user's AI conversations, most recently active first. */
  @Transactional(readOnly = true)
  public List<ConversationResponse> listAiConversations(UUID userId) {
    return conversationRepository
        .findAiConversationsForUser(userId, PageRequest.of(0, 100))
        .stream()
        .map(conversation -> mapToResponse(conversation, userId))
        .collect(Collectors.toList());
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
    memberRepository
        .findById(new ConversationMemberId(conv.getId(), userId1))
        .filter(member -> member.getLeftAt() != null)
        .ifPresent(
            member -> {
              member.setLeftAt(null);
              memberRepository.save(member);
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
            targetAvatarUrl =
                targetUser.getProfile().getProfilePictureStorageKey() != null
                    ? mediaService.profilePictureUrl(
                        targetUser.getProfile().getProfilePictureStorageKey())
                    : targetUser.getProfile().getProfilePictureUrl();
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
    Conversation conversation =
        conversationRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Conversation not found"));
    if (conversation.getType() != ConversationType.GROUP) {
      throw new BusinessRuleException("Members can only be added to group conversations");
    }
    if (request.memberIds().size() > MAX_MEMBERS_PER_REQUEST) {
      throw new BusinessRuleException("Too many members in one request");
    }
    for (UUID mId : request.memberIds()) {
      boolean activeUser =
          userRepository
              .findById(mId)
              .map(u -> u.getStatus() == null || UserStatus.ACTIVE.name().equals(u.getStatus()))
              .orElse(false);
      if (activeUser) {
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
      ConversationMember target = requireActiveMember(id, targetId);
      boolean targetPrivileged =
          ConversationMemberRole.OWNER.name().equals(target.getRole())
              || ConversationMemberRole.ADMIN.name().equals(target.getRole());
      if (targetPrivileged && !ConversationMemberRole.OWNER.name().equals(mem.getRole())) {
        throw new ForbiddenException("Admins cannot remove the owner or other admins");
      }
      if (ConversationMemberRole.OWNER.name().equals(target.getRole())) {
        throw new ForbiddenException("The owner cannot be removed");
      }
      target.setLeftAt(Instant.now());
      memberRepository.save(target);
      return;
    }
    ConversationMember self = requireActiveMember(id, targetId);
    self.setLeftAt(Instant.now());
    memberRepository.save(self);
    if (ConversationMemberRole.OWNER.name().equals(self.getRole())) promoteNextOwner(id);
  }

  @Transactional
  public void changeRole(UUID userId, UUID id, UUID targetId, ChangeRoleRequest request) {
    ConversationMember mem = requireActiveMember(id, userId);
    if (!"OWNER".equals(mem.getRole()))
      throw new ForbiddenException("Only the owner can change roles");
    if (userId.equals(targetId)) {
      throw new BusinessRuleException("The owner cannot change their own role");
    }
    ConversationMember target = requireActiveMember(id, targetId);
    target.setRole(request.role());
    memberRepository.save(target);
  }

  /** Gives OWNER to the longest-standing active member (admins first) after the owner left. */
  private void promoteNextOwner(UUID conversationId) {
    memberRepository.findByIdConversationId(conversationId).stream()
        .filter(m -> m.getLeftAt() == null)
        .min(
            java.util.Comparator.comparing(
                    (ConversationMember m) ->
                        ConversationMemberRole.ADMIN.name().equals(m.getRole()) ? 0 : 1)
                .thenComparing(
                    ConversationMember::getJoinedAt,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
        .ifPresent(
            next -> {
              next.setRole(ConversationMemberRole.OWNER.name());
              memberRepository.save(next);
            });
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

  private static final int MAX_MEMBERS_PER_REQUEST = 100;

  private ConversationMember requireActiveMember(UUID conversationId, UUID userId) {
    return memberRepository
        .findById(new ConversationMemberId(conversationId, userId))
        .filter(member -> member.getLeftAt() == null)
        .orElseThrow(() -> new ForbiddenException("Not a member of this conversation"));
  }
}
