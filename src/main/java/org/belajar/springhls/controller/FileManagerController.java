package org.belajar.springhls.controller;

import lombok.RequiredArgsConstructor;
import org.belajar.springhls.dto.request.RequestFileManager;
import org.belajar.springhls.service.FileManagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileManagerController {

    private final FileManagerService fileManagerService;

    @PostMapping("/upload")
    public ResponseEntity<Object> uploadFile(@ModelAttribute RequestFileManager request) {
        if (request.getFile() == null || request.getFile().isEmpty()) {
            return ResponseEntity.badRequest().body("File is missing or empty");
        }
        try {
            String savedFile = fileManagerService.handleUpload(request);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/videos/{videoId}/{fileName:.+}")
    public ResponseEntity<Object> streamHlsFile(
            @PathVariable String videoId,
            @PathVariable String fileName) throws Exception {
            return fileManagerService.downloadFile(videoId, fileName);
    }

    @GetMapping("/keys/{videoId}")
    public ResponseEntity<Object> getKey(@PathVariable String videoId) throws Exception {
        return fileManagerService.downloadKey(videoId);
    }


//    @GetMapping("/file/videos/{videoId}/playlist.m3u8")
//    public ResponseEntity<Object> redirectToPlaylist(@PathVariable String videoId) throws Exception {
//        return fileManagerService.downloadFile(videoId, "playlist.m3u8");
//    }

}
