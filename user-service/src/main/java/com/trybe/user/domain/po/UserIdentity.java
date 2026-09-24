package com.trybe.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trybe.user.enums.IdentityType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户登录凭据表（User 1 : N Identity）
 * </p>
 *
 * <p>唯一性由 {@code uk_type_app_identifier(identity_type, app_id, identifier)} 承担：
 * <ul>
 *   <li>PHONE 类型：app_id 恒为 ''，唯一键退化为 {@code (PHONE,'',手机号)}</li>
 *   <li>WECHAT_OPENID 类型：app_id = 小程序 appid，避免未来第二个小程序 openid 撞车</li>
 * </ul>
 * 未绑号账号不写 PHONE 凭据行 → 天然无唯一冲突，比「多 NULL 友好」更彻底。
 *
 * @author TryBe Team
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_identity")
public class UserIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID（指向 user.id）
     */
    private Long userId;

    /**
     * 凭据类型：PHONE / WECHAT_OPENID
     */
    private IdentityType identityType;

    /**
     * 应用标识：openid 必须配小程序 appid；手机号填空串
     */
    private String appId;

    /**
     * 凭据值：手机号 / openid
     */
    private String identifier;

    /**
     * 是否已验证：0否 1是（手机号需过短信验证）
     */
    private Integer verified;

    /**
     * 验证通过时间
     */
    private LocalDateTime verifiedTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
