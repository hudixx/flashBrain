package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.flashbrain.entity.Snippet;
import com.flashbrain.mapper.SnippetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
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
    public Snippet moveSnippet(String id, String userId, Double prevOrder, Double nextOrder) {
        Snippet snippet = findActiveSnippet(id, userId);

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
    public Snippet archiveSnippet(String id, String userId, String newSubjectId) {
        Snippet snippet = findActiveSnippet(id, userId);
        subjectService.ensureSubjectBelongsToUser(newSubjectId, userId);

        snippet.setSubjectId(newSubjectId);
        snippet.setSortOrder(0.0);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    public List<Snippet> getSnippetsBySubject(String subjectId, String userId) {
        subjectService.ensureSubjectBelongsToUser(subjectId, userId);
        QueryWrapper<Snippet> query = new QueryWrapper<Snippet>()
                .eq("subject_id", subjectId)
                .eq("user_id", userId)
                .and(wrapper -> wrapper.eq("is_deleted", false).or().isNull("is_deleted"))
                .orderByDesc("is_pinned")
                .orderByAsc("sort_order");
        return snippetMapper.selectList(query);
    }

    public List<Snippet> getDeletedSnippetsBySubject(String subjectId, String userId) {
        subjectService.findSubjectIncludingDeleted(subjectId, userId);
        QueryWrapper<Snippet> query = new QueryWrapper<Snippet>()
                .eq("subject_id", subjectId)
                .eq("user_id", userId)
                .eq("is_deleted", true)
                .orderByDesc("deleted_at");
        return snippetMapper.selectList(query);
    }

    @Transactional
    public Snippet createSnippet(Snippet snippet, String userId) {
        subjectService.ensureSubjectBelongsToUser(snippet.getSubjectId(), userId);
        snippet.setId(null);
        snippet.setUserId(userId);
        snippet.setIsDeleted(false);
        snippet.setDeletedAt(null);
        snippet.setDeletedBySubject(false);
        if (snippet.getSortOrder() == null) {
            snippet.setSortOrder(System.currentTimeMillis() / 1000.0);
        }
        snippetMapper.insert(snippet);
        return snippet;
    }

    @Transactional
    public Snippet updateSnippet(String id, String userId, Snippet detail) {
        Snippet snippet = findActiveSnippet(id, userId);
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
    public Snippet updateOcr(String id, String userId, String ocrText) {
        Snippet snippet = findActiveSnippet(id, userId);
        snippet.setOcrText(ocrText);
        snippet.setOcrTextVersion(nextOcrTextVersion(snippet.getOcrTextVersion()));
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet replaceOcrIfVersionMatches(String id, String userId, String ocrText, Long expectedVersion) {
        Snippet snippet = findActiveSnippet(id, userId);
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
    public boolean replaceOcrIfVersionStillMatches(String id, String userId, String ocrText, Long expectedVersion) {
        Long version = normalizeVersion(expectedVersion);
        UpdateWrapper<Snippet> update = new UpdateWrapper<Snippet>()
                .eq("id", id)
                .eq("user_id", userId)
                .and(wrapper -> wrapper.eq("is_deleted", false).or().isNull("is_deleted"))
                .set("ocr_text", ocrText)
                .set("ocr_text_version", version + 1);
        if (version == 0L) {
            update.and(wrapper -> wrapper.eq("ocr_text_version", version).or().isNull("ocr_text_version"));
        } else {
            update.eq("ocr_text_version", version);
        }
        return snippetMapper.update(null, update) > 0;
    }

    public Snippet getSnippet(String id, String userId) {
        return findActiveSnippet(id, userId);
    }

    @Transactional
    public Snippet updateNote(String id, String userId, String noteContent) {
        Snippet snippet = findActiveSnippet(id, userId);
        snippet.setNoteContent(noteContent);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet togglePin(String id, String userId) {
        Snippet snippet = findActiveSnippet(id, userId);
        snippet.setIsPinned(!Boolean.TRUE.equals(snippet.getIsPinned()));
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public Snippet toggleMastered(String id, String userId) {
        Snippet snippet = findActiveSnippet(id, userId);
        snippet.setIsMastered(!Boolean.TRUE.equals(snippet.getIsMastered()));
        if (Boolean.TRUE.equals(snippet.getIsMastered())) {
            snippet.setIsPinned(false);
        }
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public void deleteSnippet(String id, String userId) {
        Snippet snippet = findActiveSnippet(id, userId);
        markSnippetDeleted(snippet, LocalDateTime.now(), false);
        snippetMapper.updateById(snippet);
    }

    @Transactional
    public void batchDeleteSnippets(List<String> ids, String userId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        QueryWrapper<Snippet> query = new QueryWrapper<Snippet>()
                .in("id", ids)
                .eq("user_id", userId)
                .and(wrapper -> wrapper.eq("is_deleted", false).or().isNull("is_deleted"));
        List<Snippet> snippets = snippetMapper.selectList(query);
        LocalDateTime now = LocalDateTime.now();
        for (Snippet snippet : snippets) {
            markSnippetDeleted(snippet, now, false);
            snippetMapper.updateById(snippet);
        }
    }

    @Transactional
    public Snippet restoreSnippet(String id, String userId) {
        Snippet snippet = findDeletedSnippet(id, userId);
        subjectService.restoreSubjectOnly(snippet.getSubjectId(), userId);
        snippet.setIsDeleted(false);
        snippet.setDeletedAt(null);
        snippet.setDeletedBySubject(false);
        snippetMapper.updateById(snippet);
        return snippet;
    }

    @Transactional
    public void permanentDeleteSnippet(String id, String userId) {
        findDeletedSnippet(id, userId);
        snippetImageService.deleteImagesBySnippetId(id);
        snippetMapper.deleteById(id);
    }

    public void ensureSnippetBelongsToUser(String id, String userId) {
        findActiveSnippet(id, userId);
    }

    private void markSnippetDeleted(Snippet snippet, LocalDateTime deletedAt, boolean deletedBySubject) {
        snippet.setIsDeleted(true);
        snippet.setDeletedAt(deletedAt);
        snippet.setDeletedBySubject(deletedBySubject);
    }

    private Snippet findActiveSnippet(String id, String userId) {
        QueryWrapper<Snippet> query = baseSnippetQuery(id, userId)
                .and(wrapper -> wrapper.eq("is_deleted", false).or().isNull("is_deleted"))
                .last("LIMIT 1");
        return requireSnippet(query);
    }

    private Snippet findDeletedSnippet(String id, String userId) {
        QueryWrapper<Snippet> query = baseSnippetQuery(id, userId)
                .eq("is_deleted", true)
                .last("LIMIT 1");
        return requireSnippet(query);
    }

    private QueryWrapper<Snippet> baseSnippetQuery(String id, String userId) {
        return new QueryWrapper<Snippet>()
                .eq("id", id)
                .eq("user_id", userId);
    }

    private Snippet requireSnippet(QueryWrapper<Snippet> query) {
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
