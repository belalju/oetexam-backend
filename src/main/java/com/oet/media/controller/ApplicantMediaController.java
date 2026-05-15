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
    public ResponseEntity<ResourceRegion> streamAudio(
            @PathVariable String filename,
            @RequestHeader HttpHeaders headers) throws IOException {

        Resource resource = mediaStorageService.loadAudioFile(filename);
        MediaType contentType = resolveContentType(filename);

        List<HttpRange> ranges = headers.getRange();
        ResourceRegion region;

        if (ranges.isEmpty()) {
            long contentLength = resource.contentLength();
            long rangeLength = Math.min(CHUNK_SIZE, contentLength);
            region = new ResourceRegion(resource, 0, rangeLength);
        } else {
            HttpRange range = ranges.getFirst();
            long contentLength = resource.contentLength();
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(CHUNK_SIZE, end - start + 1);
            region = new ResourceRegion(resource, start, rangeLength);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    private MediaType resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        return MediaType.parseMediaType("audio/mpeg");
    }
}