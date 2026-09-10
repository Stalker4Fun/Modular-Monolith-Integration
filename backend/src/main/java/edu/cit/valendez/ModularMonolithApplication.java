package edu.cit.valendez;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@SpringBootApplication
public class ModularMonolithApplication {

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(ModularMonolithApplication.class, args);
    }

    private static void loadDotEnvIfPresent() {
        String[] possiblePaths = { ".env", "../.env", "backend/.env" };
        for (String path : possiblePaths) {
            File envFile = new File(path);
            if (envFile.exists() && envFile.isFile()) {
                try {
                    List<String> lines = Files.readAllLines(envFile.toPath());
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        int eqIdx = trimmed.indexOf('=');
                        if (eqIdx > 0) {
                            String key = trimmed.substring(0, eqIdx).trim();
                            String value = trimmed.substring(eqIdx + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                    System.out.println("[INFO] Loaded database configuration from: " + envFile.getAbsolutePath());
                    break;
                } catch (Exception e) {
                    System.err.println("[WARN] Could not load .env file from " + path + ": " + e.getMessage());
                }
            }
        }
    }
}
