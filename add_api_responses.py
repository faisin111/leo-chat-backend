import os
import re

controllers_dir = 'src/main/java/com/example/chat_app_backend'

# Match @XMapping, optional newlines, and public ResponseEntity<something> methodName(
mapping_pattern = re.compile(r'(@(Get|Post|Put|Patch|Delete)Mapping\([^)]*\))\s*public\s+ResponseEntity<(.+?)>\s+(\w+)\(')

import_responses = "import io.swagger.v3.oas.annotations.responses.ApiResponses;\nimport io.swagger.v3.oas.annotations.responses.ApiResponse;\nimport io.swagger.v3.oas.annotations.media.Content;\nimport io.swagger.v3.oas.annotations.media.Schema;\n"

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    if '@RestController' not in content:
        return

    # Add imports if not present
    if 'io.swagger.v3.oas.annotations.responses.ApiResponse' not in content:
        content = content.replace('import org.springframework.web.bind.annotation.RestController;', 'import org.springframework.web.bind.annotation.RestController;\n' + import_responses)
    
    def replacer(match):
        mapping = match.group(1)
        return_type = match.group(3) # e.g. java.util.Map<String, Object> or MessageResponse
        
        # Strip generic types for the .class reference
        raw_type = return_type.split('<')[0].strip()
        
        if raw_type == 'Void':
            schema_attr = ''
        else:
            schema_attr = f', content = @Content(schema = @Schema(implementation = {raw_type}.class))'
            
        status_code = "200"
        if "Post" in match.group(2):
             status_code = "200"
            
        api_response = f'@ApiResponse(responseCode = "{status_code}", description = "Successful response"{schema_attr})'
        
        return f'{api_response}\n  {mapping}\n  public ResponseEntity<{return_type}> {match.group(4)}('

    new_content = mapping_pattern.sub(replacer, content)
    
    with open(filepath, 'w') as f:
        f.write(new_content)

for root, _, files in os.walk(controllers_dir):
    for file in files:
        if file.endswith('Controller.java'):
            process_file(os.path.join(root, file))

