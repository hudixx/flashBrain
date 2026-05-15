package com.flashbrain.repository;

import com.flashbrain.entity.Subject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class SubjectRepositoryTest {

    @Autowired
    private SubjectRepository subjectRepository;

    @Test
    public void shouldSaveSubject() {
        Subject subject = new Subject();
        subject.setName("Java");
        subject.setIcon("java-icon");

        Subject saved = subjectRepository.save(subject);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Java");
    }
}
