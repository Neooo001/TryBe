package com.trybe.creator.mapper;

import com.trybe.creator.domain.dto.UserBrief;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 只读 user 表 Mapper（含共享库内只读统计）
 *
 * <p>creator-service 禁止写 user 表：这里只提供 {@code @Select} 查询，不继承 BaseMapper，
 * 从而在编译期就杜绝 update/delete 调用。角色/昵称这类高频读取由 RoleGuard + Redis 缓存兜底。
 *
 * <p>另有 {@code creator_application} 的当日新增统计 —— 该表归属 user-service（审核写在 user-service），
 * creator-service 仅做只读看板统计。
 */
public interface UserReadMapper {

    /**
     * 按用户ID查询角色
     *
     * @param userId 用户ID
     * @return 角色字符串：USER/CREATOR/ADMIN；用户不存在返回 null
     */
    @Select("SELECT role FROM `user` WHERE id = #{userId}")
    String selectRoleById(@Param("userId") Long userId);

    /**
     * 按用户ID查询昵称
     */
    @Select("SELECT nickname FROM `user` WHERE id = #{userId}")
    String selectNicknameById(@Param("userId") Long userId);

    /**
     * 批量查询 id + nickname（审核列表展示创作者昵称）
     */
    @Select({
            "<script>",
            "SELECT id, nickname FROM `user` WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<UserBrief> selectBriefsByIds(@Param("ids") Collection<Long> ids);

    /**
     * 统计角色=CREATOR 的用户数（运营看板 totalCreators）
     */
    @Select("SELECT COUNT(*) FROM `user` WHERE role = 'CREATOR'")
    Long countCreators();

    /**
     * 统计当日新增入驻申请数（运营看板 todayNewApplications，只读 creator_application）
     */
    @Select("SELECT COUNT(*) FROM `creator_application` WHERE create_time >= #{dayStart}")
    Long countTodayApplications(@Param("dayStart") LocalDateTime dayStart);
}
