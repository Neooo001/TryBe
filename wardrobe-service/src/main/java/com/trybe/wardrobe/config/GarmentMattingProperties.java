package com.trybe.wardrobe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 衣服抠图（去背景 → 白底留白）配置
 *
 * <p>对接「阿里云视觉智能开放平台 - 分割抠图」的 {@code SegmentCommodity}（商品分割）。
 * 该接口返回像素级前景图（不重绘商品），且 {@code ReturnForm=whiteBK} 可直接输出纯白底，
 * 正是「只把衣服抠出来、其余留白」所需的效果。
 *
 * <pre>
 * trybe:
 *   ai:
 *     matting:
 *       enabled: false                     # 默认关闭：未开通分割抠图服务时不影响现有导入流程
 *       endpoint: imageseg.cn-shanghai.aliyuncs.com   # 分割抠图服务接入点（与 regionId 对应）
 *       region-id: cn-shanghai             # 接口 RegionId 参数
 *       access-key-id: LTAIxxxx            # 阿里云主账号 AK（可复用 shared-oss.yaml 里那套；勿硬编码）
 *       access-key-secret: xxxx
 *       return-form: whiteBK               # whiteBK-白底图 / crop-裁剪边缘空白 / mask-二值遮罩
 *       timeout-ms: 10000                  # 单次调用超时
 *       max-bytes: 5242880                 # 结果图体积上限保护（超过即放弃，降级本地裁剪）
 * </pre>
 *
 * <p><b>为什么默认 enabled=false</b>：抠图会调用云端付费/限额接口，
 * 用户未显式开通「分割抠图」服务并授权 AK 时，绝不能因为一次失败影响导入主流程。
 * 关闭状态下 {@code GarmentMattingService} Bean 不会创建，导入自动走本地 bbox 裁剪。
 */
@Data
@Component
@ConfigurationProperties(prefix = "trybe.ai.matting")
public class GarmentMattingProperties {

    /** 是否启用抠图；关闭后导入流程退化为「本地 bbox 矩形裁剪 / 原图」 */
    private Boolean enabled = false;

    /** 分割抠图服务接入点 */
    private String endpoint = "imageseg.cn-shanghai.aliyuncs.com";

    /** 接口 RegionId，需与 endpoint 对应 */
    private String regionId = "cn-shanghai";

    /** 阿里云 AccessKeyId（建议走 Nacos 配置注入，勿硬编码） */
    private String accessKeyId;

    /** 阿里云 AccessKeySecret（严禁写入日志） */
    private String accessKeySecret;

    /** 返回形态：whiteBK-白底图 / crop-裁剪边缘空白 / mask-二值遮罩 */
    private String returnForm = "whiteBK";

    /** 单次调用超时（毫秒） */
    private Integer timeoutMs = 10000;

    /** 结果图体积上限（字节），超过即视为异常并降级，避免异常大图拖垮内存与 OSS */
    private Integer maxBytes = 5242880;
}
