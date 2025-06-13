package org.belajar.springhls.service;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.belajar.springhls.config.MinioUtils;
import org.belajar.springhls.dto.request.RequestFileManager;
import org.belajar.springhls.model.FileManager;
import org.belajar.springhls.repository.FileManagerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
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

    public ResponseEntity<Object> downloadFile(String videoId, String fileName) throws Exception {
        log.info("videoId:{}", videoId);
        String objectName = videoId + "/" + fileName;

        InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build());

        MediaType contentType;
        if (fileName.endsWith(".m3u8")) {
            contentType = MediaType.parseMediaType("application/x-mpegURL");
        } else if (fileName.endsWith(".ts")) {
            contentType = MediaType.parseMediaType("video/MP2T");
        } else if (fileName.endsWith(".key")) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        } else {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        byte[] bytes = is.readAllBytes();

        log.info("Read playlist.m3u8: {} bytes", bytes.length);

        return ResponseEntity.ok()
                .contentType(contentType)
                .header("Access-Control-Allow-Origin", "*")
                .body(new InputStreamResource(is));
    }

    public ResponseEntity<Object> downloadKey(String keyName) throws Exception {
        try (InputStream stream = minioUtils.getFile(bucketName, keyName+"/key.key")) {
            byte[] keyBytes = stream.readAllBytes();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(keyBytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
        fileManager.setFilePath(fileName.replace(".mp4", "") + "/playlist.m3u8");
        fileRepository.save(fileManager);

        Path tempDir = Files.createTempDirectory("video-upload");
        File inputFile = tempDir.resolve(originalFilename).toFile();
        request.getFile().transferTo(inputFile);

        File convertedMp4 = tempDir.resolve("converted.mp4").toFile();

        String conversionResult = convertToMp4WithFfmpeg(inputFile, convertedMp4);
        if (!conversionResult.equalsIgnoreCase("Success")) {
            FileUtils.deleteDirectory(tempDir.toFile());
            return "FFmpeg conversion failed";
        }

        Path hlsOutputDir = tempDir.resolve("hls");
        Files.createDirectories(hlsOutputDir);
        String packagingResult = packageWithShakaWithAes18(convertedMp4, hlsOutputDir.toFile());
        if (!packagingResult.equalsIgnoreCase("Success")) {
            FileUtils.deleteDirectory(tempDir.toFile());
            return "Shaka packaging failed";
        }

        String videoFolder = fileName.replace(".mp4", "");
        File[] hlsFiles = hlsOutputDir.toFile().listFiles();
        if (hlsFiles != null) {
            for (File hlsFile : hlsFiles) {
                try (InputStream is = new FileInputStream(hlsFile)) {
                    String objectName = videoFolder + "/" + hlsFile.getName();
                    minioUtils.uploadFile(bucketName, objectName, is);
                }
            }
        }
        File keyFile = new File(hlsOutputDir.toFile(), "key.key");
        log.info("Looking for key.key at: {}", keyFile.getAbsolutePath());

        if (keyFile.exists()) {
            log.info("key.key found! Uploading to MinIO...");
            String keyObjectName = videoFolder + "/key.key";
            try (InputStream keyStream = new FileInputStream(keyFile)) {
                minioUtils.uploadFile(bucketName, keyObjectName, keyStream);
            }
        } else {
            log.warn("key.key NOT FOUND at: {}", keyFile.getAbsolutePath());
        }


        FileUtils.deleteDirectory(tempDir.toFile());
        return "Success";
    }




    private String convertToMp4WithFfmpeg(File inputFile, File outputFile) throws IOException, InterruptedException {
        List<String> command = List.of(
                "ffmpeg",
                "-i", inputFile.getAbsolutePath(),
                "-c:v", "libx264",
                "-preset", "fast",
                "-c:a", "aac",
                "-strict", "experimental",
                "-movflags", "+faststart",
                outputFile.getAbsolutePath()
        );

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) log.info(line);
        }

        int exitCode = process.waitFor();
        return exitCode == 0 ? "Success" : "Fail";
    }

    private String packageWithShaka(File inputMp4, File outputDir) throws IOException, InterruptedException {
        String inputFileName = inputMp4.getName();
        String hostInputDir = inputMp4.getParentFile().getAbsolutePath();
        String hostOutputDir = outputDir.getAbsolutePath();

        String dockerImage = "google/shaka-packager";

        List<String> command = List.of(
                "docker", "run", "--rm",
                "-v", hostInputDir + ":/input",
                "-v", hostOutputDir + ":/output",
                dockerImage,
                "packager",
                "input=/input/" + inputFileName + ",stream=audio,output=/output/audio.mp4",
                "input=/input/" + inputFileName + ",stream=video,output=/output/video.mp4",
                "--hls_master_playlist_output", "/output/playlist.m3u8"
        );

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) log.info(line);
        }

        int exitCode = process.waitFor();
        return exitCode == 0 ? "Success" : "Fail";
    }

    private String packageWithShakaWithAes18(File inputMp4, File outputDir) throws IOException, InterruptedException {
        String inputFileName = inputMp4.getName();
        String hostInputDir = inputMp4.getParentFile().getAbsolutePath();
        String hostOutputDir = outputDir.getAbsolutePath();

        String dockerImage = "google/shaka-packager";

        String aesKeyHex = loadAesKeyFromResources();

        String keyUri = "http://localhost:8080/keys/key.key";
        String keyIdHex = generateHexKeyId();

        if (!aesKeyHex.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("AES key invalid hex format");
        }
        if (!keyIdHex.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException("Key ID invalid hex format");
        }


//        String packagerCmd = String.format(
//                "packager " +
//                        "input=/input/%s,stream=audio,output=/output/audio.mp4 " +
//                        "input=/input/%s,stream=video,output=/output/video.mp4 " +
//                        "--enable_raw_key_encryption " +
//                        "--keys \"key_id=%s:key=%s\" " +
//                        "--hls_key_uri %s " +
//                        "--hls_master_playlist_output=/output/playlist.m3u8",
//                inputFileName,
//                inputFileName,
//                keyIdHex,
//                aesKeyHex,
//                keyUri
//        );

        String packagerCmd = String.format(
                "packager " +
                        "input=/input/%s,stream=video,format=ts,segment_template=/output/video_\\$Number\\$.ts,playlist_name=video.m3u8 " +
                        "input=/input/%s,stream=audio,format=ts,segment_template=/output/audio_\\$Number\\$.ts,playlist_name=audio.m3u8 " +
                        "--enable_raw_key_encryption " +
                        "--keys key_id=%s:key=%s " +
                        "--hls_key_uri=%s " +
                        "--hls_master_playlist_output=/output/playlist.m3u8",
                inputFileName,
                inputFileName,
                keyIdHex,
                aesKeyHex,
                keyUri
        );


        log.info(packagerCmd);

        List<String> command = List.of(
                "docker", "run", "--rm",
                "-v", hostInputDir + ":/input",
                "-v", hostOutputDir + ":/output",
                dockerImage,
                "sh", "-c",
                packagerCmd
        );


        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) log.info(line);
        }

        int exitCode = process.waitFor();
        byte[] keyBytes = hexStringToByteArray(aesKeyHex);
        File keyFile = new File(outputDir, "key.key");
        Files.write(keyFile.toPath(), keyBytes);

        return exitCode == 0 ? "Success" : "Fail";
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }


    public String loadAesKeyFromResources() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("keys/key.key")) {
            if (is == null) throw new FileNotFoundException("Key not found in resources");
            byte[] keyBytes = is.readNBytes(16);
            if (keyBytes.length != 16) throw new IOException("Invalid AES-128 key length");

            StringBuilder hexKey = new StringBuilder();
            for (byte b : keyBytes) {
                hexKey.append(String.format("%02x", b));
            }
            return hexKey.toString();
        }
    }

    public String generateHexKeyId() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }





}
