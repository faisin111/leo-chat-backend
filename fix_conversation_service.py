import re

filepath = 'src/main/java/com/example/chat_app_backend/chat/conversation/ConversationService.java'
with open(filepath, 'r') as f:
    content = f.read()

# 1. Update mapToResponse calls
content = re.sub(r'mapToResponse\(c\)', r'mapToResponse(c, userId)', content)
# In getUserConversations: map(this::mapToResponse) -> map(c -> mapToResponse(c, userId))
content = content.replace('map(this::mapToResponse)', 'map(c -> mapToResponse(c, userId))')
# In getConversation: mapToResponse(conv) -> mapToResponse(conv, userId)
content = content.replace('mapToResponse(conv)', 'mapToResponse(conv, userId)')
# In getOrCreateDirectChat: userId1 is the current user ID
content = content.replace('mapToResponse(conv, userId)', 'mapToResponse(conv, userId1)', 1) # First occurrence is getOrCreateDirectChat
# Wait, actually getOrCreateDirectChat uses `conv`, so `mapToResponse(conv)` was replaced by `mapToResponse(conv, userId)` but it should be `userId1`. Let's just fix that carefully.

# Let's revert and do exact replaces to be safe:
