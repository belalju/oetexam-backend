package com.oet.media.controller;

import com.oet.common.util.ApiResponse;
import com.oet.media.service.MediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaStorageService mediaStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAudio(
            @RequestParam("file") MultipartFile file) {
        String filename = mediaStorageService.storeAudioFile(file);
        String url = "/api/media/" + filename;
        return ResponseEntity.ok(ApiResponse.success(Map.of("filename", filename, "url", url)));
    }

    @GetMapping("/{filename}")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPLICANT')")
    public ResponseEntity<?> streamAudio(
            @PathVariable String filename,
            @RequestHeader HttpHeaders headers) throws IOException {

        Resource resource = mediaStorageService.loadAudioFile(filename);
        MediaType contentType = filename.endsWith(".wav")
                ? MediaType.parseMediaType("audio/wav")
                : MediaType.parseMediaType("audio/mpeg");
        long contentLength = resource.contentLength();

        List<HttpRange> ranges = headers.getRange();

        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(contentLength)
                    .body(resource);
        }

        HttpRange range = ranges.getFirst();
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long rangeLength = Math.min(1024 * 1024L, end - start + 1);
        ResourceRegion region = new ResourceRegion(resource, start, rangeLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    @DeleteMapping("/{filename}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAudio(@PathVariable String filename) {
        mediaStorageService.deleteAudioFile(filename);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
