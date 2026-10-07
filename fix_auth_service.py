import re
filepath = 'src/main/java/com/example/chat_app_backend/auth/AuthService.java'
with open(filepath, 'r') as f:
    content = f.read()

# Remove the field autowiring
content = content.replace('@org.springframework.beans.factory.annotation.Autowired\n  private com.example.chat_app_backend.service.EmailService emailService;', 'private final com.example.chat_app_backend.service.EmailService emailService;')

# Update constructor
old_constructor = """  public AuthService(
      UserRepository userRepository,
      PasswordEncoder encoder,
      AuthenticationManager authenticationManager,
      JwtUtils jwtUtils,
      RefreshTokenRepository refreshTokenRepository,
      VerificationTokenRepository verificationTokenRepository) {"""
new_constructor = """  public AuthService(
      UserRepository userRepository,
      PasswordEncoder encoder,
      AuthenticationManager authenticationManager,
      JwtUtils jwtUtils,
      RefreshTokenRepository refreshTokenRepository,
      VerificationTokenRepository verificationTokenRepository,
      com.example.chat_app_backend.service.EmailService emailService) {"""
content = content.replace(old_constructor, new_constructor)

# Initialize in constructor
content = content.replace(
    'this.verificationTokenRepository = verificationTokenRepository;\n  }',
    'this.verificationTokenRepository = verificationTokenRepository;\n    this.emailService = emailService;\n  }'
)

with open(filepath, 'w') as f:
    f.write(content)
