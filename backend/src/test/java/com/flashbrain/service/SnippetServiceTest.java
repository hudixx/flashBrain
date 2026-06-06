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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnippetServiceTest {

    @Mock
    private SnippetMapper snippetMapper;

    @Mock
    private SnippetImageService snippetImageService;

    @InjectMocks
    private SnippetService snippetService;

    @Test
    void shouldQuerySnippetsBySubjectWithPinAndSortOrder() {
        Snippet snippet = new Snippet();
        snippet.setSubjectId(7L);
        when(snippetMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(snippet));

        List<Snippet> result = snippetService.getSnippetsBySubject(7L);

        assertThat(result).containsExactly(snippet);
        ArgumentCaptor<QueryWrapper<Snippet>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(snippetMapper).selectList(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("subject_id");
        assertThat(sqlSegment).contains("ORDER BY is_pinned DESC,sort_order ASC");
    }

    @Test
    void shouldSetDefaultSortOrderWhenCreatingSnippet() {
        Snippet snippet = new Snippet();
        snippet.setTitle("New");

        Snippet result = snippetService.createSnippet(snippet);

        verify(snippetMapper).insert(snippet);
        assertThat(result.getSortOrder()).isNotNull();
    }

    @Test
    void shouldMoveSnippetBetweenOrders() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet(1L, 100.0, 300.0);

        assertThat(result.getSortOrder()).isEqualTo(200.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldMoveSnippetBeforeFirstOrder() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet(1L, null, 300.0);

        assertThat(result.getSortOrder()).isEqualTo(150.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldMoveSnippetAfterLastOrder() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet(1L, 300.0, null);

        assertThat(result.getSortOrder()).isEqualTo(1300.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldMoveFirstSnippetToDefaultOrder() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.moveSnippet(1L, null, null);

        assertThat(result.getSortOrder()).isEqualTo(1000.0);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldToggleMasteredAndUnpinWhenMarkedMastered() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        snippet.setIsPinned(true);
        snippet.setIsMastered(false);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        Snippet result = snippetService.toggleMastered(1L);

        assertThat(result.getIsMastered()).isTrue();
        assertThat(result.getIsPinned()).isFalse();
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldAllowClearingSnippetTextFieldsToNull() {
        Snippet existing = new Snippet();
        existing.setId(1L);
        existing.setTitle("old title");
        existing.setOcrText("old ocr");
        existing.setNoteContent("old note");
        Snippet detail = new Snippet();
        when(snippetMapper.selectById(1L)).thenReturn(existing);

        Snippet result = snippetService.updateSnippet(1L, detail);

        assertThat(result.getTitle()).isNull();
        assertThat(result.getOcrText()).isNull();
        assertThat(result.getNoteContent()).isNull();
        verify(snippetMapper).updateById(existing);
    }

    @Test
    void shouldDeleteSnippetImagesWhenDeletingSnippet() {
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(snippetMapper.selectById(1L)).thenReturn(snippet);

        snippetService.deleteSnippet(1L);

        verify(snippetImageService).deleteImagesBySnippetId(1L);
        verify(snippetMapper).deleteById(1L);
    }

    @Test
    void shouldThrowWhenSnippetMissing() {
        when(snippetMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> snippetService.updateNote(99L, "note"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Snippet not found");
    }
}
