package com.flashbrain.service;

import com.flashbrain.entity.Snippet;
import com.flashbrain.repository.SnippetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SnippetService {

    @Autowired
    private SnippetRepository snippetRepository;

    /**
     * 实现浮点排序逻辑 (Lexical Ordering)
     * new_order = (prev_order + next_order) / 2
     */
    @Transactional
    public Snippet moveSnippet(Long id, Double prevOrder, Double nextOrder) {
        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Snippet not found"));
        
        double newOrder;
        if (prevOrder == null && nextOrder == null) {
            newOrder = 1000.0; // 默认第一个
        } else if (prevOrder == null) {
            newOrder = nextOrder / 2.0;
        } else if (nextOrder == null) {
            newOrder = prevOrder + 1000.0;
        } else {
            newOrder = (prevOrder + nextOrder) / 2.0;
        }
        
        snippet.setSortOrder(newOrder);
        return snippetRepository.save(snippet);
    }

    /**
     * 物理搬家逻辑：修改所属科目
     */
    @Transactional
    public Snippet archiveSnippet(Long id, Long newSubjectId) {
        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Snippet not found"));
        
        snippet.setSubjectId(newSubjectId);
        // 搬家后通常放到新科目的顶部
        snippet.setSortOrder(0.0); 
        return snippetRepository.save(snippet);
    }

    public List<Snippet> getSnippetsBySubject(Long subjectId) {
        return snippetRepository.findBySubjectIdOrderByIsPinnedDescSortOrderAsc(subjectId);
    }

    @Transactional
    public Snippet createSnippet(Snippet snippet) {
        if (snippet.getSortOrder() == null) {
            snippet.setSortOrder(System.currentTimeMillis() / 1000.0);
        }
        return snippetRepository.save(snippet);
    }

    @Transactional
    public Snippet updateSnippet(Long id, Snippet detail) {
        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Snippet not found"));
        snippet.setTitle(detail.getTitle());
        snippet.setOcrText(detail.getOcrText());
        snippet.setNoteContent(detail.getNoteContent());
        return snippetRepository.save(snippet);
    }

    @Transactional
    public Snippet updateOcr(Long id, String ocrText) {
        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Snippet not found"));
        snippet.setOcrText(ocrText);
        return snippetRepository.save(snippet);
    }

    @Transactional
    public Snippet updateNote(Long id, String noteContent) {
        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Snippet not found"));
        snippet.setNoteContent(noteContent);
        return snippetRepository.save(snippet);
    }

    @Transactional
    public Snippet togglePin(Long id) {
        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Snippet not found"));
        snippet.setIsPinned(!snippet.getIsPinned());
        return snippetRepository.save(snippet);
    }

    @Transactional
    public Snippet toggleMastered(Long id) {
        Snippet snippet = snippetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Snippet not found"));
        snippet.setIsMastered(!snippet.getIsMastered());
        // 如果标记为掌握，则自动取消置顶（符合 SRS F-3.2）
        if (snippet.getIsMastered()) {
            snippet.setIsPinned(false);
        }
        return snippetRepository.save(snippet);
    }

    @Transactional
    public void deleteSnippet(Long id) {
        snippetRepository.deleteById(id);
    }
}
