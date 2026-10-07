import re

filepath = 'src/main/java/com/example/chat_app_backend/service/EmailService.java'
with open(filepath, 'r') as f:
    content = f.read()

content = content.replace(
    'String verificationUrl = publicUrl + "/api/v1/auth/verify-email?token=" + token;',
    'String verificationUrl = publicUrl + "/verify-email?token=" + token;'
)

with open(filepath, 'w') as f:
    f.write(content)
