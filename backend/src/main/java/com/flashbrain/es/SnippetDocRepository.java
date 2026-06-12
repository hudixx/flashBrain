package com.flashbrain.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SnippetDocRepository extends ElasticsearchRepository<SnippetDoc, String> {
}
