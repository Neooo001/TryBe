package com.trybe.creator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trybe.creator.domain.po.WebUserBrief;

/**
 * web_user 只读投影 Mapper（个人中心账号资料段，包扫描注册）
 *
 * <p><b>红线</b>：只允许 selectById 等读操作；creator-service 禁止对
 * web_user 做 insert/update/delete（账号写入归属 user-service）。
 *
 * @author TryBe Team
 */
public interface WebUserBriefMapper extends BaseMapper<WebUserBrief> {
}
