package com.trybe.creator.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 更新当前创作者个人中心资料请求（PUT /creator/profile）
 *
 * <p><b>白名单映射</b>：本 DTO 只含公开主页资料的可自助编辑字段。
 * 以下字段<b>不接受、提交即忽略</b>（服务端无对应绑定，天然拒绝越权）：
 * <ul>
 *   <li>身份类 — creatorId / 审核与统计字段（归属其他模块，不进个人资料接口）</li>
 *   <li>治理类 — homepageStatus（主页展示状态仅管理端可改，创作者只读）</li>
 *   <li>账号类 — 手机号 / 账号昵称 / 密码（走 user-service 专有接口，且手机号不可改）</li>
 * </ul>
 *
 * <p><b>字段语义</b>（沿用 ItemSaveReq 红线⑧）：
 * <pre>
 *   nickname   必填，每次提交都必须携带（trim 后 1~32 字符）
 *   bio        null=不修改；""=清空；非空=覆盖（≤512 字符）
 *   avatarKey  null=不修改；""=恢复默认头像；非空=必须是本人头像归档位 objectKey
 *   coverKey   null=不修改；""=恢复默认封面；非空=必须是本人封面归档位 objectKey
 *   fieldCodes null=不修改；[] =清空；非空=覆盖（每个 code 必须合法，去重后最多 5 个）
 * </pre>
 */
@Data
@ApiModel(description = "更新创作者个人中心资料请求")
public class CreatorProfileUpdateReq {

    @ApiModelProperty(value = "创作者昵称（公开主页展示名，必填，≤32字符）", required = true)
    @NotBlank(message = "创作者昵称不能为空")
    @Size(max = 32, message = "昵称不能超过32个字符")
    private String nickname;

    @ApiModelProperty("头像 OSS objectKey（AVATAR 场景上传确认后回传；空串=恢复默认头像；null=不修改）")
    @Size(max = 255, message = "头像路径过长")
    private String avatarKey;

    @ApiModelProperty("主页封面图 OSS objectKey（COVER 场景上传确认后回传；空串=恢复默认封面；null=不修改）")
    @Size(max = 255, message = "封面图路径过长")
    private String coverKey;

    @ApiModelProperty("个人简介（≤512字符；空串=清空；null=不修改）")
    @Size(max = 512, message = "简介不能超过512个字符")
    private String bio;

    @ApiModelProperty("创作方向编码列表（可选项见 GET /creator/profile 的 fieldOptions；最多5个；null=不修改）")
    private List<String> fieldCodes;
}
