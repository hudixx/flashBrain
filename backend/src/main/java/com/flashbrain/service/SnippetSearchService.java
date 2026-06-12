package com.flashbrain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.flashbrain.entity.Snippet;
import com.flashbrain.es.SnippetDoc;
import com.flashbrain.es.SnippetDocRepository;
import com.flashbrain.mapper.SnippetMapper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SnippetSearchService {

    @Autowired
    private SnippetDocRepository snippetDocRepository;

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    private SnippetMapper snippetMapper;

    /**
     * Converts MySQL Snippet to ES SnippetDoc
     */
    private SnippetDoc convertToDoc(Snippet snippet) {
        if (snippet == null) return null;
        SnippetDoc doc = new SnippetDoc();
        BeanUtils.copyProperties(snippet, doc);
        // Ensure null booleans are set to false for safe ES mapping
        if (doc.getIsDeleted() == null) doc.setIsDeleted(false);
        if (doc.getIsPinned() == null) doc.setIsPinned(false);
        if (doc.getIsMastered() == null) doc.setIsMastered(false);
        return doc;
    }

    /**
     * Sync single snippet to ES (Upsert)
     */
    public void syncToEs(Snippet snippet) {
        try {
            SnippetDoc doc = convertToDoc(snippet);
            snippetDocRepository.save(doc);
        } catch (Exception e) {
            log.error("Failed to sync snippet to ES: " + snippet.getId(), e);
        }
    }

    /**
     * Remove single snippet from ES
     */
    public void deleteFromEs(String id) {
        try {
            snippetDocRepository.deleteById(id);
        } catch (Exception e) {
            log.error("Failed to delete snippet from ES: " + id, e);
        }
    }

    /**
     * Sync all MySQL snippets to ES (used by Admin / Manual sync API)
     */
    public void syncAllToEs() {
        log.info("Starting full sync from MySQL to Elasticsearch...");
        List<Snippet> allSnippets = snippetMapper.selectList(new QueryWrapper<>());
        List<SnippetDoc> docs = new ArrayList<>();
        for (Snippet snippet : allSnippets) {
            docs.add(convertToDoc(snippet));
        }
        snippetDocRepository.saveAll(docs);
        log.info("Successfully synced {} snippets to Elasticsearch.", docs.size());
    }

    /**
     * Full-text search using Elasticsearch
     */
    public List<Snippet> searchSnippets(String keyword, String subjectId, String userId, boolean includeDeleted) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 1. Must match userId
        boolQuery.must(QueryBuilders.termQuery("userId", userId));

        // 2. Must match deleted status
        boolQuery.must(QueryBuilders.termQuery("isDeleted", includeDeleted));

        // 3. Match specific subject if provided
        if (subjectId != null && !subjectId.trim().isEmpty()) {
            boolQuery.must(QueryBuilders.termQuery("subjectId", subjectId));
        }

        // 4. Keyword search across title, ocrText, and noteContent
        if (keyword != null && !keyword.trim().isEmpty()) {
            boolQuery.must(QueryBuilders.multiMatchQuery(keyword, "title", "ocrText", "noteContent")
                    .type(org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.PHRASE_PREFIX));
        }

        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
                .withQuery(boolQuery)
                // Order by pinned then sortOrder, similar to standard retrieval
                .withSorts(
                        org.elasticsearch.search.sort.SortBuilders.fieldSort("isPinned").order(org.elasticsearch.search.sort.SortOrder.DESC),
                        org.elasticsearch.search.sort.SortBuilders.fieldSort("sortOrder").order(org.elasticsearch.search.sort.SortOrder.ASC)
                )
                // Returning up to 1000 items as we don't have pagination yet
                .withPageable(PageRequest.of(0, 1000));

        SearchHits<SnippetDoc> searchHits = elasticsearchRestTemplate.search(queryBuilder.build(), SnippetDoc.class);

        List<Snippet> result = new ArrayList<>();
        for (SearchHit<SnippetDoc> hit : searchHits) {
            SnippetDoc doc = hit.getContent();
            Snippet snippet = new Snippet();
            BeanUtils.copyProperties(doc, snippet);
            result.add(snippet);
        }
        return result;
    }
}
