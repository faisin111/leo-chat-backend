import re

filepath = 'src/main/java/com/example/chat_app_backend/chat/message/MessageService.java'
with open(filepath, 'r') as f:
    content = f.read()

# Add import if missing
if 'SimpMessagingTemplate' not in content:
    content = content.replace('import org.springframework.stereotype.Service;', 'import org.springframework.stereotype.Service;\nimport org.springframework.messaging.simp.SimpMessagingTemplate;')

# Add constructor injection for SimpMessagingTemplate
if 'SimpMessagingTemplate messagingTemplate;' not in content:
    content = content.replace('private final ConversationMemberRepository memberRepository;', 'private final ConversationMemberRepository memberRepository;\n  private final SimpMessagingTemplate messagingTemplate;')
    
    old_constructor = """public MessageService(
      MessageRepository messageRepository,
      ConversationRepository conversationRepository,
      ConversationMemberRepository memberRepository) {
    this.messageRepository = messageRepository;
    this.conversationRepository = conversationRepository;
    this.memberRepository = memberRepository;
  }"""
    new_constructor = """public MessageService(
      MessageRepository messageRepository,
      ConversationRepository conversationRepository,
      ConversationMemberRepository memberRepository,
      SimpMessagingTemplate messagingTemplate) {
    this.messageRepository = messageRepository;
    this.conversationRepository = conversationRepository;
    this.memberRepository = memberRepository;
    this.messagingTemplate = messagingTemplate;
  }"""
    content = content.replace(old_constructor, new_constructor)

# Broadcast inside sendMessage
old_return_send = """msg = messageRepository.save(msg);
    return mapToResponse(msg);"""
new_return_send = """msg = messageRepository.save(msg);
    MessageResponse response = mapToResponse(msg);
    messagingTemplate.convertAndSend("/topic/conversations." + convId, response);
    return response;"""
content = content.replace(old_return_send, new_return_send)

# Broadcast inside editMessage
old_return_edit = """msg = messageRepository.save(msg);
    return mapToResponse(msg);"""
new_return_edit = """msg = messageRepository.save(msg);
    MessageResponse response = mapToResponse(msg);
    messagingTemplate.convertAndSend("/topic/conversations." + convId, response);
    return response;"""
content = content.replace(old_return_edit, new_return_edit)

with open(filepath, 'w') as f:
    f.write(content)
