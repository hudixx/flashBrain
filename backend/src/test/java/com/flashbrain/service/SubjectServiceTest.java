package com.flashbrain.service;

import com.flashbrain.entity.Subject;
import com.flashbrain.mapper.SubjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectMapper subjectMapper;

    @InjectMocks
    private SubjectService subjectService;

    @Test
    void shouldListSubjects() {
        Subject subject = new Subject();
        subject.setName("Java");
        when(subjectMapper.selectList(null)).thenReturn(Collections.singletonList(subject));

        List<Subject> result = subjectService.getAll();

        assertThat(result).containsExactly(subject);
    }

    @Test
    void shouldCreateSubject() {
        Subject subject = new Subject();
        subject.setName("MySQL");

        Subject result = subjectService.create(subject);

        verify(subjectMapper).insert(subject);
        assertThat(result).isSameAs(subject);
    }
}
