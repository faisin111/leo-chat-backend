import re

filepath = 'src/main/java/com/example/chat_app_backend/chat/realtime/ChatWsController.java'
with open(filepath, 'r') as f:
    content = f.read()

# Remove the broadcast since it's handled by MessageService now
content = content.replace(
    '// Broadcast to conversation topic\n    messagingTemplate.convertAndSend("/topic/conversations." + request.conversationId(), response);',
    '// Broadcasting is now automatically handled inside messageService.sendMessage()'
)

with open(filepath, 'w') as f:
    f.write(content)
