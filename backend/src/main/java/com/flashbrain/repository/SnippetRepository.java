package com.flashbrain.repository;

import com.flashbrain.entity.Snippet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SnippetRepository extends JpaRepository<Snippet, Long> {
    List<Snippet> findBySubjectIdOrderByIsPinnedDescSortOrderAsc(Long subjectId);
    List<Snippet> findBySubjectId(Long subjectId);
}
