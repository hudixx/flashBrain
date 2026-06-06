package com.flashbrain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashbrain.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
