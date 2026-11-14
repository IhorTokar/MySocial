package com.example.social.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    /**
     * Зберегти зображення від клієнта. Тип і розширення визначаються за вмістом файлу
     * ({@link MediaFiles#imageExtension}), а не за іменем чи Content-Type, які надсилає клієнт.
     */
    public String storeImage(MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file: " + e.getMessage(), e);
        }
        return store(content, MediaFiles.imageExtension(content));
    }

    /** Зберегти вже оброблений вміст (напр. обкладинку після обрізки). @param extension з крапкою: ".jpg" */
    public String store(byte[] content, String extension) {
        try {
            Path targetPath = newFile(extension);
            Files.write(targetPath, content);
            return "/media/" + targetPath.getFileName();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    private Path newFile(String extension) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        return uploadPath.resolve(UUID.randomUUID() + extension);
    }
}