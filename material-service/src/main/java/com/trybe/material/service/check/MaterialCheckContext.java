package com.trybe.material.service.check;

import com.trybe.material.domain.enums.MaterialType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 素材校验处理器上下文 — 策略模式工厂
 * </p>
 *
 * <p>自动发现所有 {@link MaterialCheckHandler} 实现，按 checkType 路由到对应处理器。
 *
 * <p>扩展方式：新增 {@code MaterialCheckHandler} 实现类即可自动注册，无需修改此处代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialCheckContext {

    private final List<MaterialCheckHandler> handlers;

    private final Map<String, MaterialCheckHandler> handlerMap = new HashMap<>();
    private final Map<String, MaterialCheckHandler> materialTypeHandlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (MaterialCheckHandler handler : handlers) {
            handlerMap.put(handler.supportedCheckType(), handler);
            log.info("[MaterialCheckContext] 注册校验处理器: {}", handler.supportedCheckType());
        }

        // 素材类型 → 校验类型映射
        materialTypeHandlerMap.put(MaterialType.FACE.getValue(), handlerMap.get("FACE_CHECK"));
        materialTypeHandlerMap.put(MaterialType.HAND.getValue(), handlerMap.get("HAND_CHECK"));
    }

    /**
     * 根据素材类型获取对应的处理器
     *
     * @param materialType 素材类型
     * @return 对应处理器，找不到返回 null
     */
    public MaterialCheckHandler getHandler(String materialType) {
        MaterialCheckHandler handler = materialTypeHandlerMap.get(materialType);
        if (handler == null) {
            log.warn("[MaterialCheckContext] 未找到素材类型对应的处理器: materialType={}", materialType);
        }
        return handler;
    }
}