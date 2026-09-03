package com.redhun.aiswarya_ledger_api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.enabled:true}")
    private boolean firebaseEnabled;

    @Value("${firebase.credentials.path:}")
    private String credentialsPath;

    @Value("${firebase.project-id:aiswarya-ledger}")
    private String projectId;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!firebaseEnabled) {
            log.info("Firebase messaging is disabled via configuration (firebase.enabled=false)");
            return null;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        try {
            InputStream serviceAccountStream = null;

            if (credentialsPath != null && !credentialsPath.trim().isEmpty()) {
                String path = credentialsPath.trim();
                Resource resource;
                if (path.startsWith("classpath:")) {
                    resource = new ClassPathResource(path.substring("classpath:".length()));
                } else {
                    resource = new FileSystemResource(path);
                }

                if (resource.exists()) {
                    serviceAccountStream = resource.getInputStream();
                    log.info("Loading Firebase credentials from configured path: {}", path);
                } else {
                    log.warn("Configured Firebase credentials path '{}' does not exist.", path);
                }
            }

            // Fallback to default classpath file if not specified or not found
            if (serviceAccountStream == null) {
                Resource defaultClasspathResource = new ClassPathResource("firebase-service-account.json");
                if (defaultClasspathResource.exists()) {
                    serviceAccountStream = defaultClasspathResource.getInputStream();
                    log.info("Loading Firebase credentials from default classpath resource: firebase-service-account.json");
                }
            }

            GoogleCredentials credentials;
            if (serviceAccountStream != null) {
                try (InputStream stream = serviceAccountStream) {
                    credentials = GoogleCredentials.fromStream(stream);
                }
            } else {
                log.info("Attempting to load Google Application Default Credentials for Firebase...");
                try {
                    credentials = GoogleCredentials.getApplicationDefault();
                } catch (Exception e) {
                    log.warn("Firebase credentials not configured or found. Firebase push notifications will be inactive. Error: {}", e.getMessage());
                    return null;
                }
                if (credentials.createScopedRequired()) {
                    credentials = credentials.createScoped(java.util.List.of(
                            "https://www.googleapis.com/auth/firebase.messaging",
                            "https://www.googleapis.com/auth/cloud-platform"
                    ));
                }
            }

            // Determine effective project ID
            String effectiveProjectId = null;
            if (credentials instanceof ServiceAccountCredentials sac) {
                effectiveProjectId = sac.getProjectId();
            }
            if (effectiveProjectId == null || effectiveProjectId.isBlank()) {
                if (projectId != null && !projectId.isBlank()) {
                    effectiveProjectId = projectId.trim();
                }
            }

            FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                    .setCredentials(credentials);

            if (effectiveProjectId != null && !effectiveProjectId.isBlank()) {
                optionsBuilder.setProjectId(effectiveProjectId);
            }

            FirebaseOptions options = optionsBuilder.build();
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("FirebaseApp initialized successfully for project: {}", options.getProjectId());
            return app;
        } catch (Exception e) {
            log.error("Failed to initialize FirebaseApp: {}", e.getMessage(), e);
            return null;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        if (firebaseApp == null) {
            log.info("FirebaseMessaging bean not created because FirebaseApp is null.");
            return null;
        }
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
