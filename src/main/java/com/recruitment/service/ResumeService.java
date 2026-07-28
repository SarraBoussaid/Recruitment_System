package com.recruitment.service;

import com.recruitment.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Service
public class ResumeService {

    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final Path uploadDir;

    public ResumeService(
            JdbcTemplate jdbc,
            @Value("${app.upload.dir:uploads/resumes}") String uploadDirPath
    ) throws IOException {
        this.jdbc = jdbc;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    public Map<String, String> uploadResume(MultipartFile file, int candidateId) {
        validateFile(file);

        String filename = UUID.randomUUID() + ".pdf";
        Path target = uploadDir.resolve(filename).normalize();

        if (!target.startsWith(uploadDir)) {
            throw new ApiException(400, "Invalid file path.");
        }

        try {
            file.transferTo(target);
        } catch (IOException ex) {
            throw new ApiException(500, "Could not save resume file.");
        }

        String resumeUrl = "/uploads/resumes/" + filename;
        jdbc.update("UPDATE candidates SET resume_url = ? WHERE id = ?", resumeUrl, candidateId);

        return Map.of("resumeUrl", resumeUrl, "message", "Resume uploaded successfully.");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "Please select a PDF file to upload.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(400, "File must be 5 MB or smaller.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            throw new ApiException(400, "Only PDF files are allowed.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.equals("application/pdf")) {
            throw new ApiException(400, "Only PDF files are allowed.");
        }
    }
}
