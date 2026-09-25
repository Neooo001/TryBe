package com.trybe.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trybe.user.domain.po.WebUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Web 域用户表 Mapper（{@code web_user}）
 *
 * <p>除 BaseMapper 外补充三个 Web 域专用语句：按手机号查询、按手机号加排他锁查询、
 * 以及触摸最近登录时间。
 *
 * @author TryBe Team
 */
public interface WebUserMapper extends BaseMapper<WebUser> {

    /**
     * 按手机号查询（phone 有唯一索引，最多一行）
     */
    @Select("SELECT * FROM web_user WHERE phone = #{phone}")
    WebUser selectByPhone(@Param("phone") String phone);

    /**
     * 按手机号加排他锁查询（注册事务内串行化同号并发）
     *
     * <p>与 wx 域 {@code bindOrTransferInTx} 的锁模式一致：phone 有唯一索引，
     * 命中时锁行、未命中时为间隙锁，配合 {@code DuplicateKeyException} 兜底并发。
     */
    @Select("SELECT * FROM web_user WHERE phone = #{phone} FOR UPDATE")
    WebUser selectByPhoneForUpdate(@Param("phone") String phone);

    /**
     * 更新最近登录时间（单语句，自带原子性）
     */
    @Update("UPDATE web_user SET last_login_time = NOW() WHERE id = #{id}")
    int touchLogin(@Param("id") Long id);
}
