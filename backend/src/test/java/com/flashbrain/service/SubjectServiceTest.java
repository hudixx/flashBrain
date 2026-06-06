package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.entity.Subject;
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
    void shouldRejectSubjectOwnedByOtherUser() {
        when(subjectMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> subjectService.ensureSubjectBelongsToUser(7L, 3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Subject not found");
    }
}
