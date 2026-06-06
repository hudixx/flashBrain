package com.flashbrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashbrain.entity.Snippet;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SnippetMapper extends BaseMapper<Snippet> {
}
