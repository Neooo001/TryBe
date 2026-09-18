package com.trybe.wardrobe.service.ai;

/**
 * 衣服抠图服务抽象（像素级去背景 → 白底留白）
 *
 * <p>当前唯一实现为 {@link AliyunImageSegMattingService}（阿里云分割抠图的商品分割接口）。
 * 抽象出接口的目的与 {@link GarmentRecognizeService} 一致：让供应商可替换，
 * 且让编排层（{@code WardrobeImportProcessor}）只依赖「能不能抠出一张图」这一语义，
 * 切换火山/自研抠图时业务层零改动。
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
     * @return 抠图结果的 OSS objectKey（白底图）
     * @throws RuntimeException 未启用 / 无凭据 / 调用失败 / 返回体异常 / 结果图下载失败
     */
    String matting(String originObjectKey, Long userId, String taskNo);

    /**
     * 当前供应商标识，用于日志打点与问题回溯
     */
    String providerName();
}
