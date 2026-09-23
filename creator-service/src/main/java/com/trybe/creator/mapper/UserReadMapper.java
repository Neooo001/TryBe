package com.trybe.creator.mapper;

import com.trybe.creator.domain.dto.UserBrief;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 只读 user 表 Mapper（含共享库内只读统计）
 *
 * <p>creator-service 禁止写 user 表：这里只提供 {@code @Select} 查询，不继承 BaseMapper，
 * 从而在编译期就杜绝 update/delete 调用。角色/昵称这类高频读取由 RoleGuard + Redis 缓存兜底。
 *
 * <p>创作者入驻申请机制已下线（改为创作者后台网页自助注册，
 * 注册即在 user-service 写入 role=CREATOR），本 Mapper 不再统计
 * {@code creator_application}，也不再提供任何与之相关的查询。
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
     * 批量查询 id + nickname（跨表只读展示创作者昵称）
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
}
