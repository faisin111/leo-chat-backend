import re
filepath = 'src/main/java/com/example/chat_app_backend/auth/repo/VerificationTokenRepository.java'
with open(filepath, 'r') as f:
    content = f.read()

content = content.replace('@org.springframework.data.jpa.repository.Modifying\n', '')
with open(filepath, 'w') as f:
    f.write(content)
