package com.trybe.creator.domain.enums;

import com.trybe.common.exception.BadRequestException;
import com.trybe.common.utils.FileTypeUtils;
import lombok.Getter;

/**
 * 创作者后台上传场景
 *
 * <p>场景决定 OSS 归档路径（见 CreatorOssPathBuilder）与校验规则：
 * ITEM_CUTOUT 为试穿素材，强制 PNG（透明底），confirm 时额外做 alpha 兜底校验。
 */
@Getter
public enum UploadScene {

    /** 物品主图原图 */
    ITEM_ORIGIN("ITEM_ORIGIN", "物品主图"),
    /** 透明底 PNG（试穿素材），强制 png */
    ITEM_CUTOUT("ITEM_CUTOUT", "透明底PNG"),
    /** 物品参考图 */
    ITEM_REFERENCE("ITEM_REFERENCE", "参考图"),
    /** 模特上身图 */
    MODEL_IMAGE("MODEL_IMAGE", "模特图"),
    ;

    private final String code;
    private final String desc;

    UploadScene(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 严格解析：非法场景直接抛参数错误（100）
     */
    public static UploadScene strictOf(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BadRequestException("上传场景不能为空");
        }
        for (UploadScene scene : values()) {
            if (scene.code.equalsIgnoreCase(code.trim())) {
                return scene;
            }
        }
        throw new BadRequestException("无效的上传场景: " + code);
    }

    /**
     * 校验并规范化扩展名：CUTOUT 场景强制 PNG，其余走文件类型白名单
     *
     * @param ext 原始扩展名
     * @return 规范化后的扩展名
     */
    public String normalizeExtension(String ext) {
        if (this == ITEM_CUTOUT) {
            String normalized = FileTypeUtils.normalizeExtension(ext);
            if (!"png".equals(normalized)) {
                throw new BadRequestException("透明底素材仅支持 PNG 格式");
            }
            return normalized;
        }
        String normalized = FileTypeUtils.normalizeExtension(ext);
        if (!FileTypeUtils.isSupportedImage(normalized)) {
            throw new BadRequestException("不支持的图片格式，仅支持 " + FileTypeUtils.supportedImageExtensions());
        }
        return normalized;
    }
}
