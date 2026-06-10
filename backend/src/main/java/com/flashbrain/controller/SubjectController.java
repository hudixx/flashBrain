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

    @GetMapping("/recycle")
    public List<Subject> getRecycleSubjects(@AuthenticationPrincipal UserPrincipal principal) {
        return subjectService.getRecycleSubjects(principal.getId());
    }

    @PostMapping
    public Subject create(@RequestBody Subject subject, @AuthenticationPrincipal UserPrincipal principal) {
        return subjectService.create(subject, principal.getId());
    }

    @PutMapping("/{id}")
    public Subject update(@PathVariable String id, @RequestBody Subject subject, @AuthenticationPrincipal UserPrincipal principal) {
        return subjectService.update(id, subject, principal.getId());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        subjectService.softDeleteSubject(id, principal.getId());
    }

    @PostMapping("/{id}/restore")
    public Subject restore(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        return subjectService.restoreSubject(id, principal.getId());
    }

    @DeleteMapping("/{id}/permanent")
    public void permanentDelete(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        subjectService.permanentDeleteSubject(id, principal.getId());
    }
}
