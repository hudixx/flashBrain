package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public SnippetImage saveImage(Long snippetId, MultipartFile file) throws IOException {
        Snippet snippet = snippetMapper.selectById(snippetId);
        if (snippet == null) {
            throw new RuntimeException("Snippet not found");
        }

        String originalFilename = file.getOriginalFilename() == null ? "ocr-image" : file.getOriginalFilename();
        String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedFilename = UUID.randomUUID() + "-" + safeFilename;

        Path snippetDir = Paths.get(uploadDir, "ocr-images", String.valueOf(snippetId)).toAbsolutePath().normalize();
        Files.createDirectories(snippetDir);

        Path targetPath = snippetDir.resolve(storedFilename).normalize();
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

    public List<SnippetImage> getImages(Long snippetId) {
        QueryWrapper<SnippetImage> query = new QueryWrapper<SnippetImage>()
                .eq("snippet_id", snippetId)
                .orderByAsc("created_at");
        return snippetImageMapper.selectList(query);
    }

    public void deleteImagesBySnippetId(Long snippetId) {
        QueryWrapper<SnippetImage> query = new QueryWrapper<SnippetImage>()
                .eq("snippet_id", snippetId);
        snippetImageMapper.delete(query);
        deleteSnippetImageDirectory(snippetId);
    }

    private void deleteSnippetImageDirectory(Long snippetId) {
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
