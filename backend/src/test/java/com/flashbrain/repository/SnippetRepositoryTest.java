package com.flashbrain.repository;

import com.flashbrain.entity.Snippet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class SnippetRepositoryTest {

    @Autowired
    private SnippetRepository snippetRepository;

    @Test
    public void shouldSaveAndFindBySubjectId() {
        Snippet snippet = new Snippet();
        snippet.setSubjectId(1L);
        snippet.setTitle("Test Title");
        snippet.setOcrText("Test OCR Content");
        snippet.setNoteContent("Test Note Content");
        snippet.setIsMastered(false);
        snippet.setSortOrder(1.0);

        snippetRepository.save(snippet);

        List<Snippet> snippets = snippetRepository.findBySubjectId(1L);
        assertThat(snippets).hasSize(1);
        assertThat(snippets.get(0).getTitle()).isEqualTo("Test Title");
    }
}
