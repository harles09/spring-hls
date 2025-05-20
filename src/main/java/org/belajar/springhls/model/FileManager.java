package org.belajar.springhls.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "files")
public class FileManager {
    @Id
    private String id;
    private String fileName;
    private String contentType;
    private long size;
    private String minioUrl;
    private LocalDateTime uploadTime;
    private String uploadedBy;
    private String description;
    private String filePath;
}
