package org.belajar.springhls.controller;

import lombok.RequiredArgsConstructor;
import org.belajar.springhls.dto.request.RequestFileManager;
import org.belajar.springhls.model.FileManager;
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

    @GetMapping("/{videoId}/{fileName:.+}")
    public ResponseEntity<byte[]> streamHlsFile(
            @PathVariable String videoId,
            @PathVariable String fileName) throws Exception {
            return fileManagerService.downloadFile(videoId, fileName);
        }
}
