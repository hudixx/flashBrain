package com.flashbrain.controller;

import com.flashbrain.entity.Snippet;
import com.flashbrain.entity.SnippetImage;
import com.flashbrain.security.UserPrincipal;
import com.flashbrain.service.SnippetImageService;
import com.flashbrain.service.SnippetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public List<Snippet> getBySubject(@PathVariable Long subjectId, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching snippets for subject: {}", subjectId);
        return snippetService.getSnippetsBySubject(subjectId, principal.getId());
    }

    @GetMapping("/{id}/images")
    public List<SnippetImage> getImages(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching images for snippet: {}", id);
        return snippetImageService.getImages(id, principal.getId());
    }

    @PostMapping("/{id}/move")
    public Snippet move(@PathVariable Long id,
                        @RequestParam(required = false) Double prevOrder,
                        @RequestParam(required = false) Double nextOrder,
                        @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Moving snippet: {} between {} and {}", id, prevOrder, nextOrder);
        return snippetService.moveSnippet(id, principal.getId(), prevOrder, nextOrder);
    }

    @PostMapping("/{id}/archive")
    public Snippet archive(@PathVariable Long id, @RequestParam Long newSubjectId, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Archiving snippet: {} to subject: {}", id, newSubjectId);
        return snippetService.archiveSnippet(id, principal.getId(), newSubjectId);
    }

    @PostMapping
    public Snippet create(@RequestBody Snippet snippet, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Creating new snippet: {}", snippet.getTitle());
        return snippetService.createSnippet(snippet, principal.getId());
    }

    @PutMapping("/{id}")
    public Snippet update(@PathVariable Long id, @RequestBody Snippet detail, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Updating snippet detail: {}", id);
        return snippetService.updateSnippet(id, principal.getId(), detail);
    }

    @PutMapping("/{id}/ocr")
    public Snippet updateOcr(@PathVariable Long id, @RequestBody String ocrText, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Updating OCR for snippet: {}", id);
        return snippetService.updateOcr(id, principal.getId(), ocrText);
    }

    @PutMapping("/{id}/note")
    public Snippet updateNote(@PathVariable Long id, @RequestBody String noteContent, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Updating note for snippet: {}", id);
        return snippetService.updateNote(id, principal.getId(), noteContent);
    }

    @PostMapping("/{id}/toggle-pin")
    public Snippet togglePin(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Toggling pin for snippet: {}", id);
        return snippetService.togglePin(id, principal.getId());
    }

    @PostMapping("/{id}/toggle-mastered")
    public Snippet toggleMastered(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Toggling mastered for snippet: {}", id);
        return snippetService.toggleMastered(id, principal.getId());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Deleting snippet: {}", id);
        snippetService.deleteSnippet(id, principal.getId());
    }
}
