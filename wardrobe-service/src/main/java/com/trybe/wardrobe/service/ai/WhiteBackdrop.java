package com.trybe.wardrobe.service.ai;

import com.trybe.common.utils.ImageUtils;
import lombok.extern.slf4j.Slf4j;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * 抠图产物后处理：透明前景 → <b>白底 + 去白边 + 压尺寸</b>
 *
 * <h3>为什么要自己合成白底</h3>
 * 两家抠图服务给的都是<b>带 alpha 通道</b>的前景图（百度 {@code return_form=rgba}、千问原生 RGBA），
 * 没有「白底」参数可调。透明 → 白底只能自己做：RGB 画布铺满白色，再把带 alpha 的前景叠上去。
 *
 * <h3>为什么必须去掉四周白边</h3>
 * 抠图服务<b>保留原图整块画布</b>：原图 1080×2374，输出也是 1080×2374，只是背景变白。
 * 而衣橱卡片是 {@code mode="aspectFit"}（完整显示、不裁切），于是整块画布被塞进卡片，
 * 衣服只占中间一小条 —— 一条长裤在卡片里看着像根筷子。把非空区域裁出来，卡片里才能真正放大。
 *
 * <h3>为什么用 alpha 而不是「白不白」来找边界</h3>
 * 直觉做法是「找不是白色的像素」，但衣橱里全是白衬衫、米色针织、浅灰卫衣 ——
 * 白衣服在白底上会被判成「没有内容」，直接被裁成一张空图。
 * 抠图服务返回的 alpha 通道才是真正的<u>前景轮廓</u>，与颜色无关，所以优先用它；
 * 只有在图片本身没有 alpha（如白底 JPEG 输入）时才退回白度扫描。
 *
 * <h3>为什么输出 JPEG</h3>
 * 白底之后<b>不再需要 alpha</b>，PNG 的无损编码纯属浪费：同图 JPEG 往往只有 PNG 的 1/5 ~ 1/10。
 * 衣橱列表一次 12 张，这个差别直接决定「秒开」还是「转圈」。
 */
@Slf4j
public final class WhiteBackdrop {

    /** 白底图默认长边上限：详情页够看，卡片更是绰绰有余 */
    public static final int DEFAULT_MAX_SIDE = 1600;

    /** alpha 判定阈值：alpha 高于它才算前景（吸收 PNG 边缘的极淡过渡，避免噪点撑大包围盒） */
    private static final int ALPHA_TOLERANCE = 8;

    /** 无 alpha 时退回白度扫描的白度容差：RGB 最小值与 255 的差超过它才算内容 */
    private static final int WHITE_TOLERANCE = 10;

    /** 裁切后四周保留的留白比例（相对内容尺寸），避免衣服紧贴画布边缘显得局促 */
    private static final double TRIM_PADDING_RATIO = 0.04;

    /**
     * 判定某一行/列「算内容」的最少像素数占比。
     * 用占比而不是「有没有一个像素」，否则一个噪点就能让裁切失效。
     */
    private static final double TRIM_MIN_HIT_RATIO = 0.005;

    private WhiteBackdrop() {
    }

    /**
     * 抠图结果 → 白底 JPEG（去白边 + 长边压到 maxSide）
     *
     * @param bytes   抠图服务返回的图片字节（通常带 alpha）
     * @param maxSide 长边上限，≤0 时用默认值
     * @return 白底 JPEG 字节；解码/编码失败时<b>原样返回入参</b>（宁可留一张透明图，也不能丢图）
     */
    public static byte[] toWhiteJpeg(byte[] bytes, int maxSide) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        BufferedImage source = ImageUtils.read(bytes);
        if (source == null) {
            log.warn("[WhiteBackdrop] 抠图结果解码失败，跳过白底合成 bytes={}", bytes.length);
            return bytes;
        }

        int limit = maxSide <= 0 ? DEFAULT_MAX_SIDE : maxSide;
        int width = source.getWidth();
        int height = source.getHeight();
        int currentMax = Math.max(width, height);
        double scale = currentMax > limit ? (double) limit / currentMax : 1.0;
        int targetW = Math.max(1, (int) Math.round(width * scale));
        int targetH = Math.max(1, (int) Math.round(height * scale));

        try {
            // resize 内部：TYPE_INT_RGB 画布 + 填白 + drawImage，这里正好当 alpha 合成用
            BufferedImage flattened = ImageUtils.resize(source, targetW, targetH);

            // 先在原图上按 alpha 找轮廓（原图精度更高），再换算到合成后的坐标系
            Bounds bounds = alphaBounds(source);
            if (bounds != null) {
                bounds = new Bounds(
                        (int) Math.floor(bounds.x1 * scale),
                        (int) Math.floor(bounds.y1 * scale),
                        (int) Math.ceil(bounds.x2 * scale),
                        (int) Math.ceil(bounds.y2 * scale));
            } else {
                // 没有 alpha 的输入（例如已经是白底 JPEG）：退回白度扫描
                bounds = whiteBounds(flattened);
            }

            BufferedImage output = cropWithPadding(flattened, bounds);
            byte[] jpeg = ImageUtils.writeJpeg(output);
            log.info("[WhiteBackdrop] 白底完成 {}x{} -> 合成{}x{} -> 裁剪{}x{} bytes={} -> {}",
                    width, height, targetW, targetH,
                    output.getWidth(), output.getHeight(), bytes.length, jpeg.length);
            return jpeg;
        } catch (Exception e) {
            log.warn("[WhiteBackdrop] 白底合成失败，保留原始抠图结果 err={}", e.getMessage());
            return bytes;
        }
    }

    // ==================== 找内容边界 ====================

    /**
     * 按 alpha 通道找前景包围盒（原图坐标）
     *
     * @return null 表示图片没有 alpha 通道，或整张全透明（没有前景可裁）
     */
    private static Bounds alphaBounds(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int[] rowHits = new int[height];
        int[] colHits = new int[width];
        long opaqueCount = 0;
        for (int y = 0; y < height; y++) {
            int base = y * width;
            for (int x = 0; x < width; x++) {
                int alpha = (pixels[base + x] >>> 24) & 0xFF;
                if (alpha > ALPHA_TOLERANCE) {
                    rowHits[y]++;
                    colHits[x]++;
                    opaqueCount++;
                }
            }
        }
        if (opaqueCount == 0) {
            log.info("[WhiteBackdrop] 图片全透明，保持原图不变 {}x{}", width, height);
            return null;
        }
        // 整张都不透明（等于没有 alpha 的普通照片）：没有白边可裁，直接给全图
        if (opaqueCount == (long) width * height) {
            return new Bounds(0, 0, width - 1, height - 1);
        }
        return fromHits(rowHits, colHits, width, height);
    }

    /**
     * 无 alpha 时按「不白」找包围盒（白底 JPEG 输入走这里）
     */
    private static Bounds whiteBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int[] rowHits = new int[height];
        int[] colHits = new int[width];
        for (int y = 0; y < height; y++) {
            int base = y * width;
            for (int x = 0; x < width; x++) {
                int rgb = pixels[base + x];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (255 - Math.min(r, Math.min(g, b)) > WHITE_TOLERANCE) {
                    rowHits[y]++;
                    colHits[x]++;
                }
            }
        }
        return fromHits(rowHits, colHits, width, height);
    }

    /**
     * 由行/列命中数推出包围盒；命中不足（整张空白）时返回 null
     */
    private static Bounds fromHits(int[] rowHits, int[] colHits, int width, int height) {
        int rowMin = (int) Math.max(2, width * TRIM_MIN_HIT_RATIO);
        int colMin = (int) Math.max(2, height * TRIM_MIN_HIT_RATIO);
        int top = firstAtLeast(rowHits, rowMin);
        int bottom = lastAtLeast(rowHits, rowMin);
        int left = firstAtLeast(colHits, colMin);
        int right = lastAtLeast(colHits, colMin);
        if (top < 0 || bottom < 0 || left < 0 || right < 0 || bottom <= top || right <= left) {
            return null;
        }
        return new Bounds(left, top, right, bottom);
    }

    // ==================== 裁切 ====================

    /**
     * 按包围盒裁切并补留白
     *
     * @return 裁后的图；包围盒为 null、或裁出来跟原图差不多大时返回原图（避免无意义拷贝）
     */
    private static BufferedImage cropWithPadding(BufferedImage image, Bounds bounds) {
        if (bounds == null) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 8 || height < 8) {
            return image;
        }

        int padX = (int) Math.round((bounds.x2 - bounds.x1 + 1) * TRIM_PADDING_RATIO) + 1;
        int padY = (int) Math.round((bounds.y2 - bounds.y1 + 1) * TRIM_PADDING_RATIO) + 1;
        int x1 = clamp(bounds.x1 - padX, 0, width - 1);
        int y1 = clamp(bounds.y1 - padY, 0, height - 1);
        int x2 = clamp(bounds.x2 + padX, 0, width - 1);
        int y2 = clamp(bounds.y2 + padY, 0, height - 1);
        int cropW = x2 - x1 + 1;
        int cropH = y2 - y1 + 1;
        if (cropW <= 0 || cropH <= 0 || cropW * cropH >= width * height) {
            return image;
        }

        // 拷成独立画布：getSubimage 共享 raster，直接交给编码器在部分 JDK 上会有隐坑
        BufferedImage cropped = new BufferedImage(cropW, cropH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cropped.createGraphics();
        try {
            g.drawImage(image, 0, 0, cropW, cropH, x1, y1, x2 + 1, y2 + 1, null);
        } finally {
            g.dispose();
        }
        return cropped;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /** 第一个「命中数 ≥ 阈值」的下标；没有则 -1 */
    private static int firstAtLeast(int[] hits, int threshold) {
        for (int i = 0; i < hits.length; i++) {
            if (hits[i] >= threshold) {
                return i;
            }
        }
        return -1;
    }

    /** 最后一个「命中数 ≥ 阈值」的下标；没有则 -1 */
    private static int lastAtLeast(int[] hits, int threshold) {
        for (int i = hits.length - 1; i >= 0; i--) {
            if (hits[i] >= threshold) {
                return i;
            }
        }
        return -1;
    }

    /** 内容包围盒（闭区间） */
    private static final class Bounds {
        private final int x1;
        private final int y1;
        private final int x2;
        private final int y2;

        private Bounds(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }
}
