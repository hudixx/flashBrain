package com.flashbrain.controller;

import com.flashbrain.dto.FilePreviewResult;
import com.flashbrain.entity.Snippet;
import com.flashbrain.entity.SnippetImage;
import com.flashbrain.security.UserPrincipal;
import com.flashbrain.service.SnippetImageService;
import com.flashbrain.service.SnippetSearchService;
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

    @Autowired
    private SnippetSearchService snippetSearchService;

    @GetMapping("/search")
    public List<Snippet> search(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String subjectId,
                                @RequestParam(defaultValue = "false") boolean global,
                                @RequestParam(defaultValue = "false") boolean includeDeleted,
                                @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Searching snippets with keyword: {}, global: {}, includeDeleted: {}", keyword, global, includeDeleted);
        String targetSubjectId = global ? null : subjectId;
        return snippetSearchService.searchSnippets(keyword, targetSubjectId, principal.getId(), includeDeleted);
    }

    @PostMapping("/sync-es")
    public void syncEs() {
        log.info("Manual trigger for Elasticsearch sync");
        snippetSearchService.syncAllToEs();
    }

    @GetMapping("/subject/{subjectId}")
    public List<Snippet> getBySubject(@PathVariable String subjectId, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching snippets for subject: {}", subjectId);
        return snippetService.getSnippetsBySubject(subjectId, principal.getId());
    }

    @GetMapping("/{id}")
    public Snippet get(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching snippet detail: {}", id);
        return snippetService.getSnippet(id, principal.getId());
    }


    @GetMapping("/recycle/subject/{subjectId}")
    public List<Snippet> getDeletedBySubject(@PathVariable String subjectId, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching deleted snippets for subject: {}", subjectId);
        return snippetService.getDeletedSnippetsBySubject(subjectId, principal.getId());
    }

    @GetMapping("/{id}/images")
    public List<SnippetImage> getImages(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching images for snippet: {}", id);
        return snippetImageService.getImages(id, principal.getId());
    }

    @GetMapping("/{id}/files")
    public List<SnippetImage> getFiles(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Fetching uploaded files for snippet: {}", id);
        return snippetImageService.getFiles(id, principal.getId());
    }

    @GetMapping("/{id}/files/{fileId}/preview")
    public FilePreviewResult previewFile(@PathVariable String id,
                                         @PathVariable String fileId,
                                         @AuthenticationPrincipal UserPrincipal principal) throws Exception {
        log.info("Previewing uploaded file: {} for snippet: {}", fileId, id);
        return snippetImageService.previewFile(id, fileId, principal.getId());
    }

    @PostMapping("/{id}/move")
    public Snippet move(@PathVariable String id,
                        @RequestParam(required = false) Double prevOrder,
                        @RequestParam(required = false) Double nextOrder,
                        @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Moving snippet: {} between {} and {}", id, prevOrder, nextOrder);
        return snippetService.moveSnippet(id, principal.getId(), prevOrder, nextOrder);
    }

    @PostMapping("/{id}/archive")
    public Snippet archive(@PathVariable String id, @RequestParam String newSubjectId, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Archiving snippet: {} to subject: {}", id, newSubjectId);
        return snippetService.archiveSnippet(id, principal.getId(), newSubjectId);
    }

    @PostMapping
    public Snippet create(@RequestBody Snippet snippet, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Creating new snippet: {}", snippet.getTitle());
        return snippetService.createSnippet(snippet, principal.getId());
    }

    @PutMapping("/{id}")
    public Snippet update(@PathVariable String id, @RequestBody Snippet detail, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Updating snippet detail: {}", id);
        return snippetService.updateSnippet(id, principal.getId(), detail);
    }

    @PutMapping("/{id}/ocr")
    public Snippet updateOcr(@PathVariable String id, @RequestBody String ocrText, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Updating OCR for snippet: {}", id);
        return snippetService.updateOcr(id, principal.getId(), ocrText);
    }

    @PutMapping("/{id}/note")
    public Snippet updateNote(@PathVariable String id, @RequestBody String noteContent, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Updating note for snippet: {}", id);
        return snippetService.updateNote(id, principal.getId(), noteContent);
    }

    @PostMapping("/{id}/toggle-pin")
    public Snippet togglePin(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Toggling pin for snippet: {}", id);
        return snippetService.togglePin(id, principal.getId());
    }

    @PostMapping("/{id}/toggle-mastered")
    public Snippet toggleMastered(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Toggling mastered for snippet: {}", id);
        return snippetService.toggleMastered(id, principal.getId());
    }

    @PostMapping("/batch-delete")
    public void batchDelete(@RequestBody List<String> ids, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Batch deleting snippets: {}", ids);
        snippetService.batchDeleteSnippets(ids, principal.getId());
    }

    @PostMapping("/{id}/restore")
    public Snippet restore(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Restoring snippet: {}", id);
        return snippetService.restoreSnippet(id, principal.getId());
    }

    @DeleteMapping("/{id}/permanent")
    public void permanentDelete(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Permanently deleting snippet: {}", id);
        snippetService.permanentDeleteSnippet(id, principal.getId());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, @AuthenticationPrincipal UserPrincipal principal) {
        log.info("Deleting snippet: {}", id);
        snippetService.deleteSnippet(id, principal.getId());
    }
}
