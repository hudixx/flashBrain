package com.flashbrain.controller;

import com.flashbrain.entity.Subject;
import com.flashbrain.repository.SubjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    @Autowired
    private SubjectRepository subjectRepository;

    @GetMapping
    public List<Subject> getAll() {
        return subjectRepository.findAll();
    }

    @PostMapping
    public Subject create(@RequestBody Subject subject) {
        return subjectRepository.save(subject);
    }
}
