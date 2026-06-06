package com.flashbrain.service;

import com.flashbrain.entity.Subject;
import com.flashbrain.mapper.SubjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubjectService {

    @Autowired
    private SubjectMapper subjectMapper;

    public List<Subject> getAll() {
        return subjectMapper.selectList(null);
    }

    public Subject create(Subject subject) {
        subjectMapper.insert(subject);
        return subject;
    }
}
