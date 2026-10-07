import re
filepath = 'src/main/java/com/example/chat_app_backend/auth/repo/VerificationTokenRepository.java'
with open(filepath, 'r') as f:
    content = f.read()

# Make sure we add back Modifying and a Query
content = content.replace(
    'void deleteByUserIdAndType(UUID userId, String type);',
    '@org.springframework.data.jpa.repository.Modifying\n  @org.springframework.data.jpa.repository.Query("DELETE FROM VerificationToken v WHERE v.userId = :userId AND v.type = :type")\n  void deleteByUserIdAndType(@org.springframework.data.repository.query.Param("userId") UUID userId, @org.springframework.data.repository.query.Param("type") String type);'
)

with open(filepath, 'w') as f:
    f.write(content)
