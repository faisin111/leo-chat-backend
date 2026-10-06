import os
import re

controllers_dir = 'src/main/java/com/example/chat_app_backend/controller'
chat_dir = 'src/main/java/com/example/chat_app_backend/chat'
auth_dir = 'src/main/java/com/example/chat_app_backend/auth'

# We want to remove the explicit content = @Content(...) from @ApiResponse where we used raw classes that broke generics.
# Example: , content = @Content(schema = @Schema(implementation = com.example.chat_app_backend.payload.response.CursorPageResponse.class))
pattern = re.compile(r',\s*content\s*=\s*@Content\(schema\s*=\s*@Schema\(implementation\s*=\s*[a-zA-Z0-9_.]+\.class\)\)')

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    new_content = pattern.sub('', content)
    
    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)

for d in [controllers_dir, chat_dir, auth_dir]:
    for root, _, files in os.walk(d):
        for file in files:
            if file.endswith('Controller.java'):
                process_file(os.path.join(root, file))

