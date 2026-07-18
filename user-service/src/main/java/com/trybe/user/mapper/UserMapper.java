package com.trybe.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.trybe.user.domain.po.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 */
public interface UserMapper extends BaseMapper<User> {

}
