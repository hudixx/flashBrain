package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.entity.Snippet;
import com.flashbrain.entity.Subject;
import com.flashbrain.mapper.SnippetMapper;
import com.flashbrain.mapper.SubjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubjectService {

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private SnippetMapper snippetMapper;

    @Autowired
    private SnippetImageService snippetImageService;

    public List<Subject> getAll(Long userId) {
        QueryWrapper<Subject> query = new QueryWrapper<Subject>()
                .eq("user_id", userId)
                .eq("is_deleted", false);
        return subjectMapper.selectList(query);
    }

    public List<Subject> getRecycleSubjects(Long userId) {
        List<Subject> subjects = subjectMapper.selectList(new QueryWrapper<Subject>()
                .eq("user_id", userId));
        List<Snippet> deletedSnippets = snippetMapper.selectList(new QueryWrapper<Snippet>()
                .eq("user_id", userId)
                .eq("is_deleted", true));

        Map<Long, Integer> deletedSnippetCountBySubject = new HashMap<>();
        Map<Long, LocalDateTime> latestSnippetDeletedAtBySubject = new HashMap<>();
        for (Snippet snippet : deletedSnippets) {
            Long subjectId = snippet.getSubjectId();
            if (subjectId == null) {
                continue;
            }
            deletedSnippetCountBySubject.merge(subjectId, 1, Integer::sum);
            LocalDateTime deletedAt = snippet.getDeletedAt();
            if (deletedAt != null) {
                latestSnippetDeletedAtBySubject.merge(subjectId, deletedAt,
                        (left, right) -> left.isAfter(right) ? left : right);
            }
        }

        List<Subject> recycleSubjects = new ArrayList<>();
        for (Subject subject : subjects) {
            int deletedSnippetCount = deletedSnippetCountBySubject.getOrDefault(subject.getId(), 0);
            boolean subjectDeleted = Boolean.TRUE.equals(subject.getIsDeleted());
            if (!subjectDeleted && deletedSnippetCount == 0) {
                continue;
            }
            subject.setDeletedSnippetCount(deletedSnippetCount);
            LocalDateTime latestSnippetDeletedAt = latestSnippetDeletedAtBySubject.get(subject.getId());
            subject.setRecycleDeletedAt(maxDeletedAt(subject.getDeletedAt(), latestSnippetDeletedAt));
            recycleSubjects.add(subject);
        }

        recycleSubjects.sort(Comparator
                .comparing(Subject::getRecycleDeletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return recycleSubjects;
    }

    public Subject create(Subject subject, Long userId) {
        subject.setId(null);
        subject.setUserId(userId);
        if (subject.getIsDeleted() == null) {
            subject.setIsDeleted(false);
        }
        subject.setDeletedAt(null);
        subjectMapper.insert(subject);
        return subject;
    }

    @Transactional
    public Subject update(Long id, Subject detail, Long userId) {
        Subject subject = findActiveSubject(id, userId);
        subject.setName(detail.getName());
        subjectMapper.updateById(subject);
        return subject;
    }

    @Transactional
    public void softDeleteSubject(Long id, Long userId) {
        Subject subject = findActiveSubject(id, userId);
        LocalDateTime now = LocalDateTime.now();
        subject.setIsDeleted(true);
        subject.setDeletedAt(now);
        subjectMapper.updateById(subject);

        List<Snippet> snippets = snippetMapper.selectList(new QueryWrapper<Snippet>()
                .eq("subject_id", id)
                .eq("user_id", userId)
                .and(wrapper -> wrapper.eq("is_deleted", false).or().isNull("is_deleted")));
        for (Snippet snippet : snippets) {
            snippet.setIsDeleted(true);
            snippet.setDeletedAt(now);
            snippet.setDeletedBySubject(true);
            snippetMapper.updateById(snippet);
        }
    }

    @Transactional
    public Subject restoreSubject(Long id, Long userId) {
        Subject subject = findDeletedSubject(id, userId);
        restoreSubjectFields(subject);
        subjectMapper.updateById(subject);

        List<Snippet> snippets = snippetMapper.selectList(new QueryWrapper<Snippet>()
                .eq("subject_id", id)
                .eq("user_id", userId)
                .eq("is_deleted", true)
                .eq("deleted_by_subject", true));
        for (Snippet snippet : snippets) {
            snippet.setIsDeleted(false);
            snippet.setDeletedAt(null);
            snippet.setDeletedBySubject(false);
            snippetMapper.updateById(snippet);
        }
        return subject;
    }

    @Transactional
    public Subject restoreSubjectOnly(Long id, Long userId) {
        Subject subject = findSubjectIncludingDeleted(id, userId);
        if (Boolean.TRUE.equals(subject.getIsDeleted())) {
            restoreSubjectFields(subject);
            subjectMapper.updateById(subject);
        }
        return subject;
    }

    @Transactional
    public void permanentDeleteSubject(Long id, Long userId) {
        findDeletedSubject(id, userId);
        List<Snippet> snippets = snippetMapper.selectList(new QueryWrapper<Snippet>()
                .eq("subject_id", id)
                .eq("user_id", userId));
        for (Snippet snippet : snippets) {
            snippetImageService.deleteImagesBySnippetId(snippet.getId());
            snippetMapper.deleteById(snippet.getId());
        }
        subjectMapper.deleteById(id);
    }

    public void ensureSubjectBelongsToUser(Long subjectId, Long userId) {
        if (subjectId == null) {
            throw new RuntimeException("Subject not found");
        }
        QueryWrapper<Subject> query = new QueryWrapper<Subject>()
                .eq("id", subjectId)
                .eq("user_id", userId)
                .eq("is_deleted", false);
        if (subjectMapper.selectCount(query) == 0) {
            throw new RuntimeException("Subject not found");
        }
    }

    public Subject findSubjectIncludingDeleted(Long subjectId, Long userId) {
        if (subjectId == null) {
            throw new RuntimeException("Subject not found");
        }
        QueryWrapper<Subject> query = new QueryWrapper<Subject>()
                .eq("id", subjectId)
                .eq("user_id", userId)
                .last("LIMIT 1");
        Subject subject = subjectMapper.selectOne(query);
        if (subject == null) {
            throw new RuntimeException("Subject not found");
        }
        return subject;
    }

    private Subject findActiveSubject(Long id, Long userId) {
        QueryWrapper<Subject> query = new QueryWrapper<Subject>()
                .eq("id", id)
                .eq("user_id", userId)
                .eq("is_deleted", false)
                .last("LIMIT 1");
        Subject subject = subjectMapper.selectOne(query);
        if (subject == null) {
            throw new RuntimeException("Subject not found");
        }
        return subject;
    }

    private Subject findDeletedSubject(Long id, Long userId) {
        QueryWrapper<Subject> query = new QueryWrapper<Subject>()
                .eq("id", id)
                .eq("user_id", userId)
                .eq("is_deleted", true)
                .last("LIMIT 1");
        Subject subject = subjectMapper.selectOne(query);
        if (subject == null) {
            throw new RuntimeException("Subject not found");
        }
        return subject;
    }

    private void restoreSubjectFields(Subject subject) {
        subject.setIsDeleted(false);
        subject.setDeletedAt(null);
    }

    private LocalDateTime maxDeletedAt(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }
}
