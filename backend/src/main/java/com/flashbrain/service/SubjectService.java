package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.entity.Subject;
import com.flashbrain.mapper.SubjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubjectService {

    @Autowired
    private SubjectMapper subjectMapper;

    public List<Subject> getAll(Long userId) {
        QueryWrapper<Subject> query = new QueryWrapper<Subject>()
                .eq("user_id", userId)
                .eq("is_deleted", false);
        return subjectMapper.selectList(query);
    }

    public Subject create(Subject subject, Long userId) {
        subject.setId(null);
        subject.setUserId(userId);
        if (subject.getIsDeleted() == null) {
            subject.setIsDeleted(false);
        }
        subjectMapper.insert(subject);
        return subject;
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
}
