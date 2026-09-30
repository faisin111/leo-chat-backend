package com.example.chat_app_backend.chat.conversation;

import com.example.chat_app_backend.chat.conversation.dto.ConversationResponse;
import com.example.chat_app_backend.exception.NotFoundException;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository,
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
        
        String key = userId1.compareTo(userId2) < 0 
                ? userId1 + ":" + userId2 
                : userId2 + ":" + userId1;

        Conversation conv = conversationRepository.findByDirectKey(key).orElseGet(() -> {
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
    public List<ConversationResponse> getUserConversations(UUID userId) {
        return conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
                c.getStatus()
        );
    }
}
