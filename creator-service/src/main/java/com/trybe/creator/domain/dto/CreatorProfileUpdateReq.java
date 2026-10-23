package com.trybe.creator.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Size;
import java.util.List;

/**
 * 更新当前创作者「主页专属资料」请求（PUT /creator/profile）
 *
 * <p><b>账号体系 v3 起，昵称 / 头像 / 简介已移出本接口</b>：它们是「人」的属性，
 * 统一存在 {@code app_user}，由 {@code PUT /web/users/profile}（user-service）维护，
 * 这样小程序与后台改的是同一份数据。本 DTO 只保留创作者主页专属字段。
 *
 * <p><b>字段语义</b>（沿用 ItemSaveReq 红线⑧）：
 * <pre>
 *   coverKey   null=不修改；""=恢复默认封面；非空=必须是本人封面归档位 objectKey
 *   fieldCodes null=不修改；[] =清空；非空=覆盖（每个 code 必须合法，去重后最多 5 个）
 * </pre>
 *
 * <p>以下字段<b>不接受、提交即忽略</b>（服务端无对应绑定，天然拒绝越权）：
 * 昵称 / 头像 / 简介（见上）、creatorId、homepageStatus（仅管理端）、手机号与密码。
 *
 * @author TryBe Team
 */
@Data
@ApiModel(description = "更新创作者主页专属资料请求")
public class CreatorProfileUpdateReq {

    @ApiModelProperty("主页封面图 OSS objectKey（COVER 场景上传确认后回传；空串=恢复默认封面；null=不修改）")
    @Size(max = 255, message = "封面图路径过长")
    private String coverKey;

    @ApiModelProperty("创作方向编码列表（可选项见 GET /creator/profile 的 fieldOptions；最多5个；null=不修改）")
    private List<String> fieldCodes;
}
