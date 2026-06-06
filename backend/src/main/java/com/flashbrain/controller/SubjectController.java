package com.flashbrain.controller;

import com.flashbrain.entity.Subject;
import com.flashbrain.service.SubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    @Autowired
    private SubjectService subjectService;

    @GetMapping
    public List<Subject> getAll() {
        return subjectService.getAll();
    }

    @PostMapping
    public Subject create(@RequestBody Subject subject) {
        return subjectService.create(subject);
    }
}
