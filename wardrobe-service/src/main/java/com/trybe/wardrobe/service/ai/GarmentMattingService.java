package com.trybe.wardrobe.service.ai;

/**
 * 衣服抠图服务抽象（像素级去背景 → 白底留图，只留衣服）
 *
 * <p>当前唯一实现为 {@link QwenImageEditMattingService}（阿里云百炼「千问图像生成与编辑」）。
 * 抽象出接口的目的与 {@link com.trybe.common.ai.GarmentRecognizeService} 一致：让供应商可替换，
 * 且让编排层（{@code WardrobeImportProcessor}）只依赖「能不能抠出一张只有衣服的图」这一语义，
 * 将来换别家（分割模型 / 自研）时业务层零改动。
 *
 * <p><b>历史</b>：曾按阿里云视觉智能开放平台的 {@code SegmentCloth}（服饰分割）实现，
 * 该产品已于 2026-09-16 公告退市、2026-10-11 起停止新用户开通，故改为百炼（官方点名替代方案）。
 *
 * <p><b>降级契约</b>：本接口任何失败都应抛出异常，由调用方静默降级为 bbox 裁剪 / 原图，
 * 绝不允许因为抠图失败导致导入失败或丢图。
 */
public interface GarmentMattingService {

    /**
     * 抠图并把结果上传到 OSS 的 cutout 目录
     *
     * @param originObjectKey 原图 OSS objectKey（服务内部据此生成公网可访问的预签名 URL 作为接口入参）
     * @param userId          用户ID（用于生成 OSS 目标路径）
     * @param taskNo          导入任务业务号（用于生成 OSS 目标路径）
     * @param hint            要保留什么的提示（品类中文名 / 衣服主体框），可为 null。
     *                        <b>为什么要这个参数</b>：不告诉它要保留什么，模特身上的上衣 + 裤子 + 鞋
     *                        会被一起保留。生成式 provider 用品类名点名，分割式 provider 用主体框收紧范围。
     * @return 抠图结果的 OSS objectKey（白底 JPEG，长边已压到配置上限）
     * @throws RuntimeException 未启用 / 无凭据 / 调用失败 / 返回体异常 / 结果图下载失败
     */
    String matting(String originObjectKey, Long userId, String taskNo, MattingHint hint);

    /**
     * 当前供应商标识，用于日志打点与问题回溯
     */
    String providerName();
}
