package com.trybe.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 账号合并安全阀：探测某账号是否已产生业务数据。
 *
 * <p><b>为什么 user-service 要读别的服务的表</b>：账号合并是把「一个空壳账号」并入
 * 「持有该手机号的账号」。项目按「小程序必须绑号后才能进入主界面」保证空壳账号
 * 没有任何业务数据 —— 但这是产品约束，不是数据库约束。一旦将来放开先浏览后绑号，
 * 合并就会静默吞掉数据。此处的两次 count 是合并前的最后一道防线，代价极小（仅合并路径调用）。
 *
 * <p>只读，且只查索引列（{@code creator_item.creator_id} / {@code wardrobe_item.user_id}）。
 *
 * @author TryBe Team
 */
@Mapper
public interface AccountProbeMapper {

    /**
     * 该账号发布的创作作品数（含草稿/下架）
     */
    @Select("SELECT COUNT(*) FROM creator_item WHERE creator_id = #{userId}")
    int countCreatorItems(@Param("userId") Long userId);

    /**
     * 该账号的衣橱条目数
     */
    @Select("SELECT COUNT(*) FROM wardrobe_item WHERE user_id = #{userId}")
    int countWardrobeItems(@Param("userId") Long userId);
}
