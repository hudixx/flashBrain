package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.entity.Snippet;
import com.flashbrain.mapper.SnippetMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnippetServiceTest {

    @Mock
    private SnippetMapper snippetMapper;

    @Mock
    private SnippetImageService snippetImageService;

    @Mock
    private SubjectService subjectService;

    @InjectMocks
    private SnippetService snippetService;

    @Test
    void shouldQuerySnippetsBySubjectWithUserPinAndSortOrder() {
        Snippet snippet = new Snippet();
        snippet.setSubjectId("7");
        when(snippetMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(snippet));

        List<Snippet> result = snippetService.getSnippetsBySubject("7", "3");

        assertThat(result).containsExactly(snippet);
        verify(subjectService).ensureSubjectBelongsToUser("7", "3");
        ArgumentCaptor<QueryWrapper<Snippet>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(snippetMapper).selectList(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("subject_id").contains("user_id").contains("is_deleted");
        assertThat(sqlSegment).contains("ORDER BY is_pinned DESC,sort_order ASC");
    }

    @Test
    void shouldSetDefaultSortOrderAndUserWhenCreatingSnippet() {
        Snippet snippet = new Snippet();
        snippet.setTitle("New");
        snippet.setSubjectId("7");
        snippet.setUserId("99");

        Snippet result = snippetService.createSnippet(snippet, "3");

        verify(subjectService).ensureSubjectBelongsToUser("7", "3");
        verify(snippetMapper).insert(snippet);
        assertThat(result.getSortOrder()).isNotNull();
        assertThat(result.getUserId()).isEqualTo("3");
    }

    @Test
    void shouldMoveSnippetBetweenOrders() {
        Snippet snippet = new Snippet();
        snippet.setId("1");
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet("1", "3", 100.0, 300.0);

        assertThat(result.getSortOrder()).isEqualTo(200.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldMoveSnippetBeforeFirstOrder() {
        Snippet snippet = new Snippet();
        snippet.setId("1");
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet("1", "3", null, 300.0);

        assertThat(result.getSortOrder()).isEqualTo(150.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldMoveSnippetAfterLastOrder() {
        Snippet snippet = new Snippet();
        snippet.setId("1");
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet("1", "3", 300.0, null);

        assertThat(result.getSortOrder()).isEqualTo(1300.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldMoveFirstSnippetToDefaultOrder() {
        Snippet snippet = new Snippet();
        snippet.setId("1");
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet("1", "3", null, null);

        assertThat(result.getSortOrder()).isEqualTo(1000.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldToggleMasteredAndUnpinWhenMarkedMastered() {
        Snippet snippet = new Snippet();
        snippet.setId("1");
        snippet.setIsPinned(true);
        snippet.setIsMastered(false);
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);

        Snippet result = snippetService.toggleMastered("1", "3");

        assertThat(result.getIsMastered()).isTrue();
        assertThat(result.getIsPinned()).isFalse();
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldAllowClearingSnippetTextFieldsToNull() {
        Snippet existing = new Snippet();
        existing.setId("1");
        existing.setTitle("old title");
        existing.setOcrText("old ocr");
        existing.setNoteContent("old note");
        Snippet detail = new Snippet();
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        Snippet result = snippetService.updateSnippet("1", "3", detail);

        assertThat(result.getTitle()).isNull();
        assertThat(result.getOcrText()).isNull();
        assertThat(result.getNoteContent()).isNull();
        verify(snippetMapper).updateById(existing);
    }

    @Test
    void shouldIncrementOcrVersionWhenUpdatingOcrText() {
        Snippet existing = new Snippet();
        existing.setId("1");
        existing.setOcrText("old ocr");
        existing.setOcrTextVersion(4L);
        Snippet detail = new Snippet();
        detail.setOcrText("new ocr");
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        Snippet result = snippetService.updateSnippet("1", "3", detail);

        assertThat(result.getOcrText()).isEqualTo("new ocr");
        assertThat(result.getOcrTextVersion()).isEqualTo(5L);
        verify(snippetMapper).updateById(existing);
    }

    @Test
    void shouldReplaceOcrWhenVersionMatches() {
        Snippet existing = new Snippet();
        existing.setId("1");
        existing.setOcrTextVersion(2L);
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        Snippet result = snippetService.replaceOcrIfVersionMatches("1", "3", "file text", 2L);

        assertThat(result.getOcrText()).isEqualTo("file text");
        assertThat(result.getOcrTextVersion()).isEqualTo(3L);
        verify(snippetMapper).updateById(existing);
    }

    @Test
    void shouldThrowWhenReplacingOcrWithStaleVersion() {
        Snippet existing = new Snippet();
        existing.setId("1");
        existing.setOcrTextVersion(3L);
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> snippetService.replaceOcrIfVersionMatches("1", "3", "file text", 2L))
                .isInstanceOf(OcrTextVersionConflictException.class)
                .hasMessage("OCR 原文已被修改，请刷新后再上传文件");
    }

    @Test
    void shouldConditionallyReplaceOcrForAsyncResult() {
        when(snippetMapper.update(isNull(), any())).thenReturn(1);

        boolean result = snippetService.replaceOcrIfVersionStillMatches("1", "3", "ocr", 0L);

        assertThat(result).isTrue();
        verify(snippetMapper).update(isNull(), any());
    }

    @Test
    void shouldSoftDeleteSnippetWithoutDeletingImages() {
        Snippet snippet = new Snippet();
        snippet.setId("1");
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);

        snippetService.deleteSnippet("1", "3");

        assertThat(snippet.getIsDeleted()).isTrue();
        assertThat(snippet.getDeletedAt()).isNotNull();
        assertThat(snippet.getDeletedBySubject()).isFalse();
        verify(snippetImageService, never()).deleteImagesBySnippetId("1");
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldPermanentlyDeleteSnippetImagesAndSnippet() {
        Snippet snippet = new Snippet();
        snippet.setId("1");
        snippet.setIsDeleted(true);
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(snippet);

        snippetService.permanentDeleteSnippet("1", "3");

        verify(snippetImageService).deleteImagesBySnippetId("1");
        verify(snippetMapper).deleteById("1");
    }

    @Test
    void shouldThrowWhenSnippetMissingForCurrentUser() {
        when(snippetMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> snippetService.updateNote("99", "3", "note"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Snippet not found");
    }
}
