package com.trybe.material.service.check;

import com.trybe.material.domain.po.Material;

/**
 * <p>
 * 素材校验策略接口 — 策略模式抽象
 * </p>
 *
 * <p>每种素材类型可对应不同的校验实现：
 * <ul>
 *   <li>当前：Mock 模拟校验（FakeCheckHandler）</li>
 *   <li>未来：对接第三方 AI 服务（如 AIGC 人脸质量评估 API）</li>
 * </ul>
 *
 * <p>扩展方式：新建实现类 + 添加 {@code @Component} 注解即可自动注册。
 */
public interface MaterialCheckHandler {

    /**
     * 执行校验
     *
     * @param material 素材记录
     * @return 校验结果（true-通过, false-失败）
     */
    CheckResult check(Material material);

    /**
     * 支持的素材类型
     *
     * @return 素材类型值（FACECHECK / HANDCHECK）
     */
    String supportedCheckType();
}
