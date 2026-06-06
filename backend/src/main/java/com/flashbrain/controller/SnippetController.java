package com.flashbrain.controller;

import com.flashbrain.entity.Snippet;
import com.flashbrain.entity.SnippetImage;
import com.flashbrain.service.SnippetImageService;
import com.flashbrain.service.SnippetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snippets")
@Slf4j
public class SnippetController {

    @Autowired
    private SnippetService snippetService;

    @Autowired
    private SnippetImageService snippetImageService;

    @GetMapping("/subject/{subjectId}")
    public List<Snippet> getBySubject(@PathVariable Long subjectId) {
        log.info("Fetching snippets for subject: {}", subjectId);
        return snippetService.getSnippetsBySubject(subjectId);
    }

    @GetMapping("/{id}/images")
    public List<SnippetImage> getImages(@PathVariable Long id) {
        log.info("Fetching images for snippet: {}", id);
        return snippetImageService.getImages(id);
    }

    @PostMapping("/{id}/move")
    public Snippet move(@PathVariable Long id, @RequestParam(required = false) Double prevOrder, @RequestParam(required = false) Double nextOrder) {
        log.info("Moving snippet: {} between {} and {}", id, prevOrder, nextOrder);
        return snippetService.moveSnippet(id, prevOrder, nextOrder);
    }

    @PostMapping("/{id}/archive")
    public Snippet archive(@PathVariable Long id, @RequestParam Long newSubjectId) {
        log.info("Archiving snippet: {} to subject: {}", id, newSubjectId);
        return snippetService.archiveSnippet(id, newSubjectId);
    }

    @PostMapping
    public Snippet create(@RequestBody Snippet snippet) {
        log.info("Creating new snippet: {}", snippet.getTitle());
        return snippetService.createSnippet(snippet);
    }

    @PutMapping("/{id}")
    public Snippet update(@PathVariable Long id, @RequestBody Snippet detail) {
        log.info("Updating snippet detail: {}", id);
        return snippetService.updateSnippet(id, detail);
    }

    @PutMapping("/{id}/ocr")
    public Snippet updateOcr(@PathVariable Long id, @RequestBody String ocrText) {
        log.info("Updating OCR for snippet: {}", id);
        return snippetService.updateOcr(id, ocrText);
    }

    @PutMapping("/{id}/note")
    public Snippet updateNote(@PathVariable Long id, @RequestBody String noteContent) {
        log.info("Updating note for snippet: {}", id);
        // 此处补上之前缺失的 updateNote 调用
        Snippet snippet = snippetService.updateNote(id, noteContent);
        return snippet;
    }

    @PostMapping("/{id}/toggle-pin")
    public Snippet togglePin(@PathVariable Long id) {
        log.info("Toggling pin for snippet: {}", id);
        return snippetService.togglePin(id);
    }

    @PostMapping("/{id}/toggle-mastered")
    public Snippet toggleMastered(@PathVariable Long id) {
        log.info("Toggling mastered for snippet: {}", id);
        return snippetService.toggleMastered(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        log.info("Deleting snippet: {}", id);
        snippetService.deleteSnippet(id);
    }
}
