package com.trybe.material.service.check;

import cn.hutool.json.JSONUtil;
import com.trybe.material.domain.po.Material;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * Mock 人脸素材校验处理器 — 模拟实现
 * </p>
 *
 * <p>校验规则（模拟）：
 * <ul>
 *   <li>文件格式检查：仅允许 jpg/jpeg/png/webp</li>
 *   <li>文件大小检查：不超过 10MB</li>
 *   <li>AI 人脸检测：模拟，固定返回检测到人脸的置信度</li>
 * </ul>
 *
 * <p>预留扩展点：后续对接真实 AI 服务时，只需替换此实现即可。
 */
@Slf4j
@Component
public class FaceCheckHandler implements MaterialCheckHandler {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final String[] ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "webp"};

    @Override
    public CheckResult check(Material material) {
        log.info("[FaceCheck] 开始人脸素材校验 materialId={} fileName={}", material.getId(), material.getFileName());

        Map<String, Object> details = new LinkedHashMap<>();

        // 1. 文件格式校验
        boolean formatPassed = checkFormat(material, details);
        if (!formatPassed) {
            return CheckResult.fail("文件格式不支持", JSONUtil.toJsonStr(details));
        }

        // 2. 文件大小校验
        boolean sizePassed = checkSize(material, details);
        if (!sizePassed) {
            return CheckResult.fail("文件大小超出限制", JSONUtil.toJsonStr(details));
        }

        // 3. AI 人脸检测（模拟）
        boolean aiPassed = mockAiFaceDetect(material, details);
        if (!aiPassed) {
            return CheckResult.fail("人脸检测未通过", JSONUtil.toJsonStr(details));
        }

        details.put("overall", "PASS");
        log.info("[FaceCheck] 人脸素材校验通过 materialId={}", material.getId());
        return CheckResult.pass("校验通过", JSONUtil.toJsonStr(details));
    }

    @Override
    public String supportedCheckType() {
        return "FACE_CHECK";
    }

    // ==================== 子校验方法 ====================

    private boolean checkFormat(Material material, Map<String, Object> details) {
        String fileName = material.getFileName();
        if (fileName == null) {
            details.put("format", "FAIL: 文件名为空");
            return false;
        }
        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = fileName.substring(dotIdx + 1).toLowerCase();
        }
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(ext)) {
                details.put("format", "PASS: " + ext);
                return true;
            }
        }
        details.put("format", "FAIL: 不支持扩展名 " + ext + ", 允许: " + String.join(",", ALLOWED_EXTENSIONS));
        return false;
    }

    private boolean checkSize(Material material, Map<String, Object> details) {
        Long fileSize = material.getFileSize();
        if (fileSize == null || fileSize <= 0) {
            details.put("size", "FAIL: 文件大小为0");
            return false;
        }
        if (fileSize > MAX_FILE_SIZE) {
            details.put("size", String.format("FAIL: %d bytes 超过上限 %d bytes", fileSize, MAX_FILE_SIZE));
            return false;
        }
        details.put("size", "PASS: " + fileSize + " bytes");
        return true;
    }

    /**
     * 模拟 AI 人脸检测 — 预留第三方AI接入点
     *
     * <p>注意：此处为同步模拟，实际对接第三方 AI 服务时应改为异步调用，
     * 避免阻塞 MQ 消费者线程导致事务长时间持有、线程池耗尽和内存积压。
     */
    private boolean mockAiFaceDetect(Material material, Map<String, Object> details) {
        // 模拟: 95% 概率通过（无阻塞延迟）
        boolean passed = Math.random() > 0.05;
        Map<String, Object> aiResult = new LinkedHashMap<>();
        aiResult.put("has_face", passed);
        aiResult.put("confidence", passed ? 0.85 + Math.random() * 0.14 : 0.0);
        aiResult.put("simulated", true);
        details.put("ai_face_detect", aiResult);

        log.info("[FaceCheck] AI人脸检测(模拟) materialId={} passed={}", material.getId(), passed);
        return passed;
    }
}