package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.dto.FilePreviewResult;
import com.flashbrain.entity.Snippet;
import com.flashbrain.entity.SnippetImage;
import com.flashbrain.mapper.SnippetImageMapper;
import com.flashbrain.mapper.SnippetMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
public class SnippetImageService {

    @Autowired
    private SnippetImageMapper snippetImageMapper;

    @Autowired
    private SnippetMapper snippetMapper;

    @Autowired
    private FileTextExtractor fileTextExtractor;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public SnippetImage saveImage(String snippetId, String userId, MultipartFile file) throws IOException {
        return saveUploadedFile(snippetId, userId, file);
    }

    public SnippetImage saveUploadedFile(String snippetId, String userId, MultipartFile file) throws IOException {
        ensureSnippetBelongsToUser(snippetId, userId);

        String originalFilename = file.getOriginalFilename() == null ? "upload-file" : file.getOriginalFilename();
        String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedFilename = UUID.randomUUID() + "-" + safeFilename;

        Path snippetDir = Paths.get(uploadDir, "ocr-images", String.valueOf(snippetId)).toAbsolutePath().normalize();
        Files.createDirectories(snippetDir);

        Path targetPath = snippetDir.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(snippetDir)) {
            throw new IOException("Invalid upload file path");
        }
        Files.write(targetPath, file.getBytes());

        SnippetImage image = new SnippetImage();
        image.setSnippetId(snippetId);
        image.setOriginalFilename(originalFilename);
        image.setStoredFilename(storedFilename);
        image.setUrl("/uploads/ocr-images/" + snippetId + "/" + storedFilename);
        image.setCreatedAt(LocalDateTime.now());
        snippetImageMapper.insert(image);
        return image;
    }

    public List<SnippetImage> getImages(String snippetId, String userId) {
        return getFiles(snippetId, userId);
    }

    public List<SnippetImage> getFiles(String snippetId, String userId) {
        ensureSnippetBelongsToUser(snippetId, userId);
        QueryWrapper<SnippetImage> query = new QueryWrapper<SnippetImage>()
                .eq("snippet_id", snippetId)
                .orderByAsc("created_at");
        return snippetImageMapper.selectList(query);
    }

    public FilePreviewResult previewFile(String snippetId, String fileId, String userId) {
        ensureSnippetBelongsToUser(snippetId, userId);
        SnippetImage file = findSnippetFile(snippetId, fileId);
        FileKind kind = fileTextExtractor.detectKind(file.getOriginalFilename(), null, new byte[0]);
        return new FilePreviewResult(file.getId(), snippetId, file.getOriginalFilename(), kind.name(), null, file.getUrl());
    }

    public void deleteImagesBySnippetId(String snippetId) {
        QueryWrapper<SnippetImage> query = new QueryWrapper<SnippetImage>()
                .eq("snippet_id", snippetId);
        snippetImageMapper.delete(query);
        deleteSnippetImageDirectory(snippetId);
    }

    private SnippetImage findSnippetFile(String snippetId, String fileId) {
        QueryWrapper<SnippetImage> query = new QueryWrapper<SnippetImage>()
                .eq("id", fileId)
                .eq("snippet_id", snippetId)
                .last("LIMIT 1");
        SnippetImage file = snippetImageMapper.selectOne(query);
        if (file == null) {
            throw new RuntimeException("File not found");
        }
        return file;
    }

    private void ensureSnippetBelongsToUser(String snippetId, String userId) {
        QueryWrapper<Snippet> query = new QueryWrapper<Snippet>()
                .eq("id", snippetId)
                .eq("user_id", userId)
                .last("LIMIT 1");
        Snippet snippet = snippetMapper.selectOne(query);
        if (snippet == null) {
            throw new RuntimeException("Snippet not found");
        }
    }

    private void deleteSnippetImageDirectory(String snippetId) {
        Path snippetDir = Paths.get(uploadDir, "ocr-images", String.valueOf(snippetId)).toAbsolutePath().normalize();
        if (!Files.exists(snippetDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(snippetDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete snippet image file: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to delete snippet image directory: {}", snippetDir, e);
        }
    }
}
