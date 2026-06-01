package com.oet.media.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class ApplicantMediaController {

    private static final long CHUNK_SIZE = 1024 * 1024; // 1 MB per chunk

    private final MediaStorageService mediaStorageService;

    @GetMapping("/{filename}")
    @PreAuthorize("hasAnyRole('APPLICANT', 'ADMIN')")
    public ResponseEntity<?> streamAudio(
            @PathVariable String filename,
            @RequestHeader HttpHeaders headers) throws IOException {

        Resource resource = mediaStorageService.loadAudioFile(filename);
        MediaType contentType = resolveContentType(filename);
        long contentLength = resource.contentLength();

        List<HttpRange> ranges = headers.getRange();

        // No Range header: return the full file (200 OK).
        // Required for Angular HttpClient blob downloads — a 206 with only the first
        // chunk would truncate audio after ~1 minute.
        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(contentLength)
                    .body(resource);
        }

        // Range header present: return the requested chunk (206 Partial Content).
        // Used by the browser's native <audio> element for seeking and buffering.
        HttpRange range = ranges.getFirst();
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long rangeLength = Math.min(CHUNK_SIZE, end - start + 1);
        ResourceRegion region = new ResourceRegion(resource, start, rangeLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    private MediaType resolveContentType(String filename) {
        if (filename.toLowerCase().endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        return MediaType.parseMediaType("audio/mpeg");
    }
}
