package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.entity.Snippet;
import com.flashbrain.entity.Subject;
import com.flashbrain.mapper.SnippetMapper;
import com.flashbrain.mapper.SubjectMapper;
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
class SubjectServiceTest {

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private SnippetMapper snippetMapper;

    @Mock
    private SnippetImageService snippetImageService;

    @InjectMocks
    private SubjectService subjectService;

    @Test
    void shouldListSubjectsByUser() {
        Subject subject = new Subject();
        subject.setName("Java");
        when(subjectMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(subject));

        List<Subject> result = subjectService.getAll(3L);

        assertThat(result).containsExactly(subject);
        ArgumentCaptor<QueryWrapper<Subject>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(subjectMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("user_id").contains("is_deleted");
    }

    @Test
    void shouldCreateSubjectForCurrentUser() {
        Subject subject = new Subject();
        subject.setName("MySQL");
        subject.setUserId(99L);

        Subject result = subjectService.create(subject, 3L);

        verify(subjectMapper).insert(subject);
        assertThat(result).isSameAs(subject);
        assertThat(result.getUserId()).isEqualTo(3L);
    }

    @Test
    void shouldUpdateSubjectName() {
        Subject subject = new Subject();
        subject.setId(7L);
        subject.setName("Old");
        Subject detail = new Subject();
        detail.setName("New");
        when(subjectMapper.selectOne(any(QueryWrapper.class))).thenReturn(subject);

        Subject result = subjectService.update(7L, detail, 3L);

        assertThat(result.getName()).isEqualTo("New");
        verify(subjectMapper).updateById(subject);
    }

    @Test
    void shouldSoftDeleteSubjectAndItsActiveSnippets() {
        Subject subject = new Subject();
        subject.setId(7L);
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        when(subjectMapper.selectOne(any(QueryWrapper.class))).thenReturn(subject);
        when(snippetMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(snippet));

        subjectService.softDeleteSubject(7L, 3L);

        assertThat(subject.getIsDeleted()).isTrue();
        assertThat(subject.getDeletedAt()).isNotNull();
        assertThat(snippet.getIsDeleted()).isTrue();
        assertThat(snippet.getDeletedAt()).isNotNull();
        assertThat(snippet.getDeletedBySubject()).isTrue();
        verify(subjectMapper).updateById(subject);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldRestoreSubjectAndSubjectDeletedSnippets() {
        Subject subject = new Subject();
        subject.setId(7L);
        subject.setIsDeleted(true);
        Snippet snippet = new Snippet();
        snippet.setId(1L);
        snippet.setIsDeleted(true);
        snippet.setDeletedBySubject(true);
        when(subjectMapper.selectOne(any(QueryWrapper.class))).thenReturn(subject);
        when(snippetMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(snippet));

        Subject result = subjectService.restoreSubject(7L, 3L);

        assertThat(result.getIsDeleted()).isFalse();
        assertThat(result.getDeletedAt()).isNull();
        assertThat(snippet.getIsDeleted()).isFalse();
        assertThat(snippet.getDeletedAt()).isNull();
        assertThat(snippet.getDeletedBySubject()).isFalse();
        verify(subjectMapper).updateById(subject);
        verify(snippetMapper).updateById(snippet);
    }

    @Test
    void shouldRejectSubjectOwnedByOtherUser() {
        when(subjectMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> subjectService.ensureSubjectBelongsToUser(7L, 3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Subject not found");
    }
}
