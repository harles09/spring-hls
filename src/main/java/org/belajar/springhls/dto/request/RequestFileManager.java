package org.belajar.springhls.dto.request;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class RequestFileManager {
    private MultipartFile file;
    private String uploadedBy;
    private String description;
}
