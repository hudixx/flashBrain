package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.dto.FilePreviewResult;
import com.flashbrain.entity.Snippet;
import com.flashbrain.entity.SnippetImage;
import com.flashbrain.mapper.SnippetImageMapper;
import com.flashbrain.mapper.SnippetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnippetImageServiceTest {

    @Mock
    private SnippetImageMapper snippetImageMapper;

    @Mock
    private SnippetMapper snippetMapper;

    @Mock
    private FileTextExtractor fileTextExtractor;

    @InjectMocks
    private SnippetImageService snippetImageService;

    @TempDir
    Path uploadDir;

    @Test
    void shouldSaveImageFileAndInsertImageRecord() throws Exception {
        ReflectionTestUtils.setField(snippetImageService, "uploadDir", uploadDir.toString());
        Snippet snippet = new Snippet();
        snippet.setId(12L);
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);
        MockMultipartFile file = new MockMultipartFile("file", "hello world.png", "image/png", "image-content".getBytes());

        SnippetImage result = snippetImageService.saveImage(12L, 3L, file);

        ArgumentCaptor<SnippetImage> captor = ArgumentCaptor.forClass(SnippetImage.class);
        verify(snippetImageMapper).insert(captor.capture());
        SnippetImage inserted = captor.getValue();

        assertThat(inserted.getSnippetId()).isEqualTo(12L);
        assertThat(inserted.getOriginalFilename()).isEqualTo("hello world.png");
        assertThat(inserted.getStoredFilename()).endsWith("-hello_world.png");
        assertThat(inserted.getUrl()).startsWith("/uploads/ocr-images/12/");
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(result).isSameAs(inserted);
        assertThat(Files.exists(uploadDir.resolve("ocr-images").resolve("12").resolve(inserted.getStoredFilename()))).isTrue();
    }

    @Test
    void shouldSaveNonImageFileAndInsertUploadRecord() throws Exception {
        ReflectionTestUtils.setField(snippetImageService, "uploadDir", uploadDir.toString());
        Snippet snippet = new Snippet();
        snippet.setId(12L);
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);
        MockMultipartFile file = new MockMultipartFile("file", "note file.txt", "text/plain", "text-content".getBytes());

        SnippetImage result = snippetImageService.saveUploadedFile(12L, 3L, file);

        ArgumentCaptor<SnippetImage> captor = ArgumentCaptor.forClass(SnippetImage.class);
        verify(snippetImageMapper).insert(captor.capture());
        SnippetImage inserted = captor.getValue();

        assertThat(inserted.getSnippetId()).isEqualTo(12L);
        assertThat(inserted.getOriginalFilename()).isEqualTo("note file.txt");
        assertThat(inserted.getStoredFilename()).endsWith("-note_file.txt");
        assertThat(inserted.getUrl()).startsWith("/uploads/ocr-images/12/");
        assertThat(result).isSameAs(inserted);
        assertThat(Files.exists(uploadDir.resolve("ocr-images").resolve("12").resolve(inserted.getStoredFilename()))).isTrue();
    }

    @Test
    void shouldRejectImageUploadWhenSnippetDoesNotExistForCurrentUser() {
        ReflectionTestUtils.setField(snippetImageService, "uploadDir", uploadDir.toString());
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "missing.png", "image/png", "image-content".getBytes());

        assertThatThrownBy(() -> snippetImageService.saveImage(99L, 3L, file))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Snippet not found");

        verify(snippetImageMapper, never()).insert(any(SnippetImage.class));
        assertThat(Files.exists(uploadDir.resolve("ocr-images").resolve("99"))).isFalse();
    }

    @Test
    void shouldReturnPreviewMetadataWithoutExtractingDocumentText() {
        Snippet snippet = new Snippet();
        snippet.setId(12L);
        SnippetImage file = new SnippetImage();
        file.setId(7L);
        file.setSnippetId(12L);
        file.setOriginalFilename("note.docx");
        file.setStoredFilename("stored-note.docx");
        file.setUrl("/uploads/ocr-images/12/stored-note.docx");
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);
        when(snippetImageMapper.selectOne(any(QueryWrapper.class))).thenReturn(file);
        when(fileTextExtractor.detectKind("note.docx", null, new byte[0])).thenReturn(FileKind.DOCX);

        FilePreviewResult result = snippetImageService.previewFile(12L, 7L, 3L);

        assertThat(result.getOriginalFilename()).isEqualTo("note.docx");
        assertThat(result.getFileType()).isEqualTo("DOCX");
        assertThat(result.getText()).isNull();
        assertThat(result.getUrl()).isEqualTo("/uploads/ocr-images/12/stored-note.docx");
    }

    @Test
    void shouldDeleteImageRecordsAndSnippetDirectory() throws Exception {
        ReflectionTestUtils.setField(snippetImageService, "uploadDir", uploadDir.toString());
        Path snippetDir = uploadDir.resolve("ocr-images").resolve("12");
        Files.createDirectories(snippetDir);
        Files.write(snippetDir.resolve("old.png"), "image-content".getBytes());

        snippetImageService.deleteImagesBySnippetId(12L);

        verify(snippetImageMapper).delete(any());
        assertThat(Files.exists(snippetDir)).isFalse();
    }
}
