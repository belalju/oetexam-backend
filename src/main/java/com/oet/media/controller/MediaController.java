package com.oet.media.controller;

import com.oet.common.util.ApiResponse;
import com.oet.media.service.MediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaStorageService mediaStorageService;

    @PostMapping("/upload")
//    @PreAuthorize("hasRole('ADMIN')") // temporarily disabled
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAudio(
            @RequestParam("file") MultipartFile file) {
        String filename = mediaStorageService.storeAudioFile(file);
        String url = "/api/admin/media/" + filename;
        return ResponseEntity.ok(ApiResponse.success(Map.of("filename", filename, "url", url)));
    }

    @GetMapping("/{filename}")
//    @PreAuthorize("hasRole('ADMIN')") // temporarily disabled
    public ResponseEntity<Resource> streamAudio(@PathVariable String filename) {
        Resource resource = mediaStorageService.loadAudioFile(filename);

        String contentType = filename.endsWith(".wav") ? "audio/wav" : "audio/mpeg";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }

    @DeleteMapping("/{filename}")
//    @PreAuthorize("hasRole('ADMIN')") // temporarily disabled
    public ResponseEntity<ApiResponse<Void>> deleteAudio(@PathVariable String filename) {
        mediaStorageService.deleteAudioFile(filename);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
