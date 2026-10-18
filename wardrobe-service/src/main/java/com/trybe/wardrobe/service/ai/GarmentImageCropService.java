package com.trybe.wardrobe.service.ai;

import com.trybe.common.ai.GarmentRecognizeResult;
import com.trybe.common.utils.ImageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

/**
 * 衣服图片智能裁剪服务
 *
 * <p>为什么在服务端本地裁，而不是再调一个分割/抠图 API：
 * <ol>
 *   <li>Qwen-VL 已经返回了主体框 bbox，二次调用分割 API 属于重复计费</li>
 *   <li>ImageIO 属于 JDK 内置能力，零依赖、零成本、毫秒级完成</li>
 *   <li>裁剪只需「框准」，不需要像素级抠图；后续试穿环节再走抠图能力即可</li>
 * </ol>
 *
 * <p>裁剪策略：按 bbox 裁切 → 四周补 4% 留白（避免紧贴边缘显得局促）→ 输出 JPEG(q=0.9)。
 * bbox 非法或解码失败时原样返回，保证「宁可裁不好，也不能丢图」。
 *
 * <p>底层图片读写统一走 {@link ImageUtils}，本类只负责「业务语义」：
 * 留白比例、最小/最大边长、压缩档位。
 */
@Slf4j
@Service
public class GarmentImageCropService {

    /** 留白比例：在 bbox 基础上四周各外扩 4% */
    private static final double PADDING_RATIO = 0.04;

    /** 输出图最小边长，避免极端 bbox 产出不可用的缩略图 */
    private static final int MIN_SIDE = 64;

    /** 输出图最大边长，控制 OSS 存储与小程序加载成本 */
    private static final int MAX_SIDE = 1600;

    /**
     * 按归一化 bbox 裁切服装主体
     *
     * @param originBytes 原图二进制
     * @param bbox        归一化主体框，可为 null
     * @return 裁剪后的 JPEG 字节；bbox 无效或处理失败时返回原图
     */
    public byte[] cropToGarment(byte[] originBytes, GarmentRecognizeResult.Bbox bbox) {
        if (originBytes == null || originBytes.length == 0) {
            return originBytes;
        }
        if (bbox == null || !bbox.valid()) {
            log.info("[GarmentCrop] 无有效bbox，保留原图");
            return originBytes;
        }

        try {
            BufferedImage source = ImageUtils.read(originBytes);
            if (source == null) {
                log.warn("[GarmentCrop] 图片解码失败，保留原图");
                return originBytes;
            }

            int width = source.getWidth();
            int height = source.getHeight();

            // 归一化坐标 → 像素坐标，并外扩留白
            double padX = PADDING_RATIO * (bbox.getX2() - bbox.getX1());
            double padY = PADDING_RATIO * (bbox.getY2() - bbox.getY1());

            int x1 = ImageUtils.clamp(Math.floor((bbox.getX1() - padX) * width), 0, width - 1);
            int y1 = ImageUtils.clamp(Math.floor((bbox.getY1() - padY) * height), 0, height - 1);
            int x2 = ImageUtils.clamp(Math.ceil((bbox.getX2() + padX) * width), x1 + 1, width);
            int y2 = ImageUtils.clamp(Math.ceil((bbox.getY2() + padY) * height), y1 + 1, height);

            int cropW = x2 - x1;
            int cropH = y2 - y1;
            if (cropW < MIN_SIDE || cropH < MIN_SIDE) {
                log.info("[GarmentCrop] 裁剪区域过小({}x{})，保留原图", cropW, cropH);
                return originBytes;
            }

            // 超过最大边长时等比缩小；同时完成白底重绘，PNG 透明区不会变黑
            BufferedImage output = ImageUtils.resizeToMaxSide(
                    source.getSubimage(x1, y1, cropW, cropH), MAX_SIDE);

            byte[] result = ImageUtils.writeJpeg(output);
            log.info("[GarmentCrop] 裁剪完成 src={}x{} rect=({},{},{},{}) out={}x{} bytes={}",
                    width, height, x1, y1, x2, y2, output.getWidth(), output.getHeight(), result.length);
            return result;
        } catch (Exception e) {
            log.warn("[GarmentCrop] 裁剪异常，降级保留原图", e);
            return originBytes;
        }
    }

    /**
     * 送模型前压缩：超过 maxBytes 时按比例逐级缩小并降低 JPEG 质量
     *
     * <p>实现已收敛到 {@link ImageUtils#compressForModel(byte[], int)}，两个服务的压缩档位
     * （1280 → 1024 → 800）只维护一份。本方法保留为「衣橱导入链路」的语义入口。
     *
     * @return 压缩后的字节；无法压缩或已足够小则返回原图
     */
    public byte[] compressForModel(byte[] originBytes, int maxBytes) {
        return ImageUtils.compressForModel(originBytes, maxBytes);
    }
}
