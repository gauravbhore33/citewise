package com.citewise.backend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public StoredFile store(MultipartFile file) {
        try {
            byte[] head;
            try (InputStream in = file.getInputStream()) {
                head = in.readNBytes(16);
            }

            FileType type = FileType.detect(head);
            if (type == null) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Only PDF, PNG and JPEG files are allowed");
            }

            String storedName = UUID.randomUUID() + "." + type.getExtension();
            Path target = root.resolve(storedName).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalStateException("Invalid storage path");
            }

            file.transferTo(target);
            return new StoredFile(storedName, type, file.getSize());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the file", e);
        }
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(root.resolve(storedName).normalize());
        } catch (IOException e) {
            // best effort cleanup
        }
    }
}