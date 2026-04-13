package com.oet.media.service;

import com.oet.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class MediaStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("audio/mpeg", "audio/wav", "audio/mp3", "audio/x-wav");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB

    private final Path audioDir;

    public MediaStorageService(@Value("${app.upload.audio-dir:./uploads/audio/}") String audioDirPath) {
        this.audioDir = Paths.get(audioDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.audioDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create audio upload directory: " + audioDirPath, e);
        }
    }

    public String storeAudioFile(MultipartFile file) {
        validateFile(file);

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload"
        );
        String extension = getExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + "." + extension;

        Path targetPath = audioDir.resolve(storedFilename);
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Failed to store audio file: " + e.getMessage());
        }

        log.info("Audio file stored: {}", storedFilename);
        return storedFilename;
    }

    public Resource loadAudioFile(String filename) {
        String cleanName = StringUtils.cleanPath(filename);
        if (cleanName.contains("..")) {
            throw new BusinessException("Invalid filename: " + filename);
        }

        Path filePath = audioDir.resolve(cleanName).normalize();
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new BusinessException("Audio file not found: " + filename);
        } catch (MalformedURLException e) {
            throw new BusinessException("Could not load audio file: " + filename);
        }
    }

    public void deleteAudioFile(String filename) {
        String cleanName = StringUtils.cleanPath(filename);
        if (cleanName.contains("..")) {
            throw new BusinessException("Invalid filename: " + filename);
        }
        Path filePath = audioDir.resolve(cleanName).normalize();
        try {
            Files.deleteIfExists(filePath);
            log.info("Audio file deleted: {}", filename);
        } catch (IOException e) {
            log.warn("Could not delete audio file: {}", filename, e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("File size exceeds the 50MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("Only MP3 and WAV audio files are allowed");
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "mp3";
    }
}
