package com.example.chat_app_backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class ChatAppBackendApplication implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ChatAppBackendApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ChatAppBackendApplication.class, args);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String baseUrl = "http://localhost:" + port + contextPath;

        log.info("\n----------------------------------------------------------\n\t" +
                 "🚀 SERVER STARTED SUCCESSFULLY! 🚀\n\t" +
                 "API Base URL : {}/api/v1\n\t" +
                 "Swagger UI   : {}/swagger-ui.html\n\t" +
                 "API Docs     : {}/v3/api-docs\n" +
                 "----------------------------------------------------------", 
                 baseUrl, baseUrl, baseUrl);
    }
}
