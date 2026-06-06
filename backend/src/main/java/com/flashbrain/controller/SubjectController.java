package com.flashbrain.controller;

import com.flashbrain.entity.Subject;
import com.flashbrain.security.UserPrincipal;
import com.flashbrain.service.SubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    @Autowired
    private SubjectService subjectService;

    @GetMapping
    public List<Subject> getAll(@AuthenticationPrincipal UserPrincipal principal) {
        return subjectService.getAll(principal.getId());
    }

    @PostMapping
    public Subject create(@RequestBody Subject subject, @AuthenticationPrincipal UserPrincipal principal) {
        return subjectService.create(subject, principal.getId());
    }
}
