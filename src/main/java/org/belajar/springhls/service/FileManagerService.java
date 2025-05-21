package org.belajar.springhls.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.belajar.springhls.config.MinioUtils;
import org.belajar.springhls.dto.request.RequestFileManager;
import org.belajar.springhls.model.FileManager;
import org.belajar.springhls.repository.FileManagerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileManagerService {
    private final MinioUtils minioUtils;
    private final FileManagerRepository fileRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    public FileManager uploadFile(MultipartFile file, String uploadedBy, String description) {
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        log.info("bucketName:{}", bucketName);
        minioUtils.uploadFile(bucketName, file, fileName, file.getContentType());

        FileManager fileDoc = FileManager.builder()
                .fileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .minioUrl(fileName)
                .uploadTime(LocalDateTime.now())
                .uploadedBy(uploadedBy)
                .description(description)
                .build();

        return fileRepository.save(fileDoc);
    }

    public ResponseEntity<byte[]> downloadFile(String videoId, String fileName) throws Exception {
        String objectName = videoId + "/" + fileName;

        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build())) {

            byte[] bytes = stream.readAllBytes();

            String contentType;
            if (fileName.endsWith(".m3u8")) {
                contentType = "application/vnd.apple.mpegurl";
            } else if (fileName.endsWith(".ts")) {
                contentType = "video/MP2T";
            } else {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes);
        }
    }

    public String handleUpload(RequestFileManager request) throws Exception {
        String originalFilename = request.getFile().getOriginalFilename();
        String fileName = UUID.randomUUID() + "_" + originalFilename;


        FileManager fileManager = new FileManager();
        fileManager.setFileName(fileName);
        fileManager.setUploadedBy(request.getUploadedBy());
        fileManager.setDescription(request.getDescription());
        fileManager.setUploadTime(LocalDateTime.now());
        fileManager.setFilePath(fileName.replace(".mp4", "") + "/playlist.m3u8"); // example path
        fileRepository.save(fileManager);

        Path tempDir = Files.createTempDirectory("video-upload");
        File inputFile = tempDir.resolve(originalFilename).toFile();
        request.getFile().transferTo(inputFile);

        Path hlsOutputDir = tempDir.resolve("hls");
        Files.createDirectories(hlsOutputDir);

        String result = convertToHLS(inputFile, hlsOutputDir.toFile());
        if (result.equalsIgnoreCase("FFmpeg conversion failed")) {
            return "Failed";
        }

        File[] hlsFiles = hlsOutputDir.toFile().listFiles();
        if (hlsFiles != null) {
            for (File hlsFile : hlsFiles) {
                try (InputStream is = new FileInputStream(hlsFile)) {
                    String objectName = fileName.replace(".mp4", "") + "/" + hlsFile.getName();
                    minioUtils.uploadFile(bucketName, objectName,is);
                }
            }
        }

        // Cleanup
        FileUtils.deleteDirectory(tempDir.toFile());
        return "Success";
    }


    private String convertToHLS(File input, File outputDir) throws IOException, InterruptedException {
        List<String> command = List.of(
                "ffmpeg",
                "-i", input.getAbsolutePath(),
                "-codec:", "copy",
                "-start_number", "0",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-f", "hls",
                new File(outputDir, "playlist.m3u8").getAbsolutePath()
        );

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) log.info(line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return "FFmpeg conversion failed";
        }
        return "Error";
    }
}
