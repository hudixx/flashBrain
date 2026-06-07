package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.flashbrain.entity.Snippet;
import com.flashbrain.mapper.SnippetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SnippetService {

    @Autowired
    private SnippetMapper snippetMapper;

    @Autowired
    private SnippetImageService snippetImageService;

    @Autowired
    private SubjectService subjectService;

    /**
     * 实现浮点排序逻辑 (Lexical Ordering)
     * new_order = (prev_order + next_order) / 2
     */
    @Transactional
    public Snippet moveSnippet(Long id, Long userId, Double prevOrder, Double nextOrder) {
        Snippet snippet = findSnippet(id, userId);

        double newOrder;
        if (prevOrder == null && nextOrder == null) {
            newOrder = 1000.0;
        } else if (prevOrder == null) {
            newOrder = nextOrder / 2.0;
        } else if (nextOrder == null) {
            newOrder = prevOrder + 1000.0;
        } else {
            newOrder = (prevOrder + nextOrder) / 2.0;
        }

        snippet.setSortOrder(newOrder);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    /**
     * 物理搬家逻辑：修改所属科目
     */
    @Transactional
    public Snippet archiveSnippet(Long id, Long userId, Long newSubjectId) {
        Snippet snippet = findSnippet(id, userId);
        subjectService.ensureSubjectBelongsToUser(newSubjectId, userId);

        snippet.setSubjectId(newSubjectId);
        snippet.setSortOrder(0.0);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    public List<Snippet> getSnippetsBySubject(Long subjectId, Long userId) {
        subjectService.ensureSubjectBelongsToUser(subjectId, userId);
        QueryWrapper<Snippet> query = new QueryWrapper<Snippet>()
                .eq("subject_id", subjectId)
                .eq("user_id", userId)
                .orderByDesc("is_pinned")
                .orderByAsc("sort_order");
        return snippetMapper.selectList(query);
    }

    @Transactional
    public Snippet createSnippet(Snippet snippet, Long userId) {
        subjectService.ensureSubjectBelongsToUser(snippet.getSubjectId(), userId);
        snippet.setId(null);
        snippet.setUserId(userId);
        if (snippet.getSortOrder() == null) {
            snippet.setSortOrder(System.currentTimeMillis() / 1000.0);
        }
        snippetMapper.insert(snippet);
        return snippet;
    }

    @Transactional
    public Snippet updateSnippet(Long id, Long userId, Snippet detail) {
        Snippet snippet = findSnippet(id, userId);
        snippet.setTitle(detail.getTitle());
        if (!equalsNullable(snippet.getOcrText(), detail.getOcrText())) {
            snippet.setOcrTextVersion(nextOcrTextVersion(snippet.getOcrTextVersion()));
        }
        snippet.setOcrText(detail.getOcrText());
        snippet.setNoteContent(detail.getNoteContent());
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet updateOcr(Long id, Long userId, String ocrText) {
        Snippet snippet = findSnippet(id, userId);
        snippet.setOcrText(ocrText);
        snippet.setOcrTextVersion(nextOcrTextVersion(snippet.getOcrTextVersion()));
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet replaceOcrIfVersionMatches(Long id, Long userId, String ocrText, Long expectedVersion) {
        Snippet snippet = findSnippet(id, userId);
        Long currentVersion = normalizeVersion(snippet.getOcrTextVersion());
        if (expectedVersion != null && !currentVersion.equals(expectedVersion)) {
            throw new OcrTextVersionConflictException();
        }
        snippet.setOcrText(ocrText);
        snippet.setOcrTextVersion(nextOcrTextVersion(currentVersion));
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public boolean replaceOcrIfVersionStillMatches(Long id, Long userId, String ocrText, Long expectedVersion) {
        Long version = normalizeVersion(expectedVersion);
        UpdateWrapper<Snippet> update = new UpdateWrapper<Snippet>()
                .eq("id", id)
                .eq("user_id", userId)
                .set("ocr_text", ocrText)
                .set("ocr_text_version", version + 1);
        if (version == 0L) {
            update.and(wrapper -> wrapper.eq("ocr_text_version", version).or().isNull("ocr_text_version"));
        } else {
            update.eq("ocr_text_version", version);
        }
        return snippetMapper.update(null, update) > 0;
    }

    public Snippet getSnippet(Long id, Long userId) {
        return findSnippet(id, userId);
    }

    @Transactional
    public Snippet updateNote(Long id, Long userId, String noteContent) {
        Snippet snippet = findSnippet(id, userId);
        snippet.setNoteContent(noteContent);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet togglePin(Long id, Long userId) {
        Snippet snippet = findSnippet(id, userId);
        snippet.setIsPinned(!Boolean.TRUE.equals(snippet.getIsPinned()));
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet toggleMastered(Long id, Long userId) {
        Snippet snippet = findSnippet(id, userId);
        snippet.setIsMastered(!Boolean.TRUE.equals(snippet.getIsMastered()));
        if (Boolean.TRUE.equals(snippet.getIsMastered())) {
            snippet.setIsPinned(false);
        }
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public void deleteSnippet(Long id, Long userId) {
        findSnippet(id, userId);
        snippetImageService.deleteImagesBySnippetId(id);
        snippetMapper.deleteById(id);
    }

    public void ensureSnippetBelongsToUser(Long id, Long userId) {
        findSnippet(id, userId);
    }

    private Snippet findSnippet(Long id, Long userId) {
        QueryWrapper<Snippet> query = new QueryWrapper<Snippet>()
                .eq("id", id)
                .eq("user_id", userId)
                .last("LIMIT 1");
        Snippet snippet = snippetMapper.selectOne(query);
        if (snippet == null) {
            throw new RuntimeException("Snippet not found");
        }
        if (snippet.getOcrTextVersion() == null) {
            snippet.setOcrTextVersion(0L);
        }
        return snippet;
    }

    private Long normalizeVersion(Long version) {
        return version == null ? 0L : version;
    }

    private Long nextOcrTextVersion(Long version) {
        return normalizeVersion(version) + 1;
    }

    private boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
