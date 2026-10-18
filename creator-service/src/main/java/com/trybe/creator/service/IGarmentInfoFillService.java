package com.trybe.creator.service;

/**
 * 商品信息自动回填服务
 *
 * <p>作品发布后由 {@code ItemPublishedAiFillListener} 异步调用：
 * 读取作品图片 → 调视觉大模型识别服装 → 把识别结果写入该作品的商品信息字段。
 *
 * <p>它不是对外接口（没有 controller），只服务于「发布后自动解析」这一条链路。
 */
public interface IGarmentInfoFillService {

    /**
     * 用作品图片解析并回填商品信息。
     *
     * <p><b>幂等</b>：仅当 {@code ai_parsed = 0} 时执行，因此重复发布同一作品不会覆盖
     * 创作者后来自行修改过的商品信息。
     *
     * <p><b>不抛异常</b>：这是发布成功之后的后台动作，任何失败都只记日志 ——
     * 作品已经上架，不能因为模型超时把它整体回滚或报错。
     *
     * @param creatorId 创作者用户ID（仅用于日志与归属校验）
     * @param itemId    作品ID
     */
    void fillFromItemImage(Long creatorId, Long itemId);
}
