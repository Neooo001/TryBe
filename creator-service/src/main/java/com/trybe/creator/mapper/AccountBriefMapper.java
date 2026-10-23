package com.trybe.creator.mapper;

import com.trybe.creator.domain.po.AccountBrief;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * {@code app_user} 只读投影查询（不继承 BaseMapper：手机号是关联列，不能走默认的整表映射）
 *
 * @author TryBe Team
 */
@Mapper
public interface AccountBriefMapper {

    /**
     * 按账号ID取账号资料（含当前绑定手机号）。
     *
     * <p>手机号取自 {@code user_identity(identity_type='PHONE')}，一个账号最多一条；
     * 用子查询而非 join，避免「一人多凭据」时行数放大。
     *
     * @param id 账号ID
     * @return 账号投影；不存在返回 null
     */
    @Select("SELECT u.id, u.nickname, u.avatar, u.bio, u.create_time, u.last_login_time, u.update_time, "
            + "(SELECT i.identifier FROM user_identity i "
            + "  WHERE i.user_id = u.id AND i.identity_type = 'PHONE' LIMIT 1) AS phone "
            + "FROM app_user u WHERE u.id = #{id}")
    AccountBrief selectBriefById(@Param("id") Long id);

    /**
     * 批量取账号资料（作品列表用，一次 IN 查询避免 N+1）
     *
     * @param ids 账号ID集合（调用方保证非空）
     * @return 账号投影列表
     */
    @Select("<script>"
            + "SELECT u.id, u.nickname, u.avatar, u.bio, u.create_time, u.last_login_time, u.update_time, "
            + "(SELECT i.identifier FROM user_identity i "
            + "  WHERE i.user_id = u.id AND i.identity_type = 'PHONE' LIMIT 1) AS phone "
            + "FROM app_user u WHERE u.id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<AccountBrief> selectBriefByIds(@Param("ids") Collection<Long> ids);
}
