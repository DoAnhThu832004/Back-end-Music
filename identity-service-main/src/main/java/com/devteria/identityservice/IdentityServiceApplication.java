package com.devteria.identityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

@SpringBootApplication
@EnableScheduling  // Kích hoạt Cron Job chạy offline 2h sáng mỗi ngày
public class IdentityServiceApplication {

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(IdentityServiceApplication.class, args);
    }

    /**
     * Tự động đọc file .env ở thư mục gốc (nếu có) và nạp vào System Properties
     * để Spring Boot đọc được ${GEMINI_API_KEY} khi chạy trực tiếp trong IDE mà không cần cấu hình thủ công.
     */
    private static void loadDotEnvIfPresent() {
        File[] candidateFiles = new File[]{
                new File(".env"),
                new File("identity-service-main/.env"),
                new File("../.env"),
                new File("D:/SpringBoot/identity-service-main/identity-service-main/.env")
        };

        for (File envFile : candidateFiles) {
            if (envFile.exists() && envFile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                            continue;
                        }
                        int idx = line.indexOf('=');
                        String key = line.substring(0, idx).trim();
                        String value = line.substring(idx + 1).trim();
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                    System.out.println("[EnvLoader] Da nap thanh cong cau hinh tu: " + envFile.getAbsolutePath());
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }
}
