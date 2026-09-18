package com.trybe.common.utils;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * <p>
 * 图片处理工具（JDK ImageIO 实现，零第三方依赖）
 * </p>
 *
 * <p>统一各服务的图片「解码 / 缩放 / JPEG 编码」逻辑。此前 wardrobe-service
 * 的裁剪服务和 common 的人脸/手势检测服务各自实现了一份 ImageIO 读写，
 * 一旦某处漏掉「白底重绘」，带透明通道的 PNG 转 JPEG 就会出现黑块，
 * 而问题只在特定图片上复现、极难定位。<b>收敛到一处是唯一可靠的解法。</b>
 *
 * <p>统一约定：
 * <ul>
 *   <li>输出一律 JPEG，且先重绘到白底 —— 规避透明区变黑</li>
 *   <li>缩略图使用双线性插值 + 高质量渲染</li>
 *   <li>所有方法对 null / 空数组 / 解码失败均返回安全值，不抛异常给调用方</li>
 * </ul>
 */
@Slf4j
public final class ImageUtils {

    private ImageUtils() {
    }

    /**
     * 解码图片字节为 BufferedImage
     *
     * @return 解码结果；入参为空或格式不支持时返回 null（调用方需判空）
     */
    public static BufferedImage read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            log.warn("[ImageUtils] 图片解码失败 bytes={}", bytes.length, e);
            return null;
        }
    }

    /**
     * 缩放到指定尺寸，并重绘到白底
     *
     * <p>即使目标尺寸与原图一致也走一次重绘：这是把带 alpha 通道的图
     * 规范化为 RGB 的必要步骤。
     */
    public static BufferedImage resize(BufferedImage source, int targetW, int targetH) {
        int w = Math.max(1, targetW);
        int h = Math.max(1, targetH);
        BufferedImage target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /**
     * 等比缩放到「长边不超过 maxSide」；原图已足够小则仅做白底重绘
     */
    public static BufferedImage resizeToMaxSide(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        int currentMax = Math.max(width, height);
        double scale = currentMax > maxSide ? (double) maxSide / currentMax : 1.0;
        return resize(source,
                (int) Math.round(width * scale),
                (int) Math.round(height * scale));
    }

    /**
     * 编码为 JPEG 字节
     *
     * @throws IOException JPEG 编码器不可用时抛出，由调用方决定降级策略
     */
    public static byte[] writeJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", out)) {
            throw new IOException("JPEG 编码器不可用");
        }
        return out.toByteArray();
    }

    /**
     * 把 double 截断并夹取到 [min, max]，用于像素坐标防越界
     */
    public static int clamp(double value, int min, int max) {
        int v = (int) value;
        if (v < min) {
            return min;
        }
        return Math.min(v, max);
    }
}
