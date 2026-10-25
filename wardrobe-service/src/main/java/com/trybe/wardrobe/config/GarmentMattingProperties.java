package com.trybe.wardrobe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 衣服抠图（去背景 → 透明底只留衣服）配置
 *
 * <p>两套实现可切换，靠 {@code trybe.ai.matting.provider} 选：
 * <ul>
 *   <li>{@code baidu}（默认）—— 百度智能云「智能抠图」，<b>0.03 元/次</b>，个人实名有 100 次免费；
 *       分割类模型，像素级抠图、不重绘商品细节；缺点是通用主体抠图，不能指定「只要裤子」</li>
 *   <li>{@code qwen-image-edit} —— 阿里云百炼「千问图像生成与编辑」，约 0.1 元/张、免费额度仅 10 张；
 *       能按「只保留这件长裤」点名，原生 RGBA 透明；缺点是生成式模型可能轻微重绘商品纹理</li>
 * </ul>
 *
 * <pre>
 * trybe:
 *   ai:
 *     matting:
 *       enabled: false            # 默认关闭：没配密钥时不影响现有导入流程
 *       provider: baidu           # baidu | qwen-image-edit
 *       timeout-ms: 30000
 *       max-bytes: 5242880        # 结果图体积上限保护（超过即放弃，降级本地裁剪）
 *       baidu:
 *         api-key: xxx            # 百度智能云应用的 API Key
 *         secret-key: xxx         # 同一个应用的 Secret Key
 *         base-url: https://aip.baidubce.com/rest/2.0/image-process/v1/segment
 *         token-url: https://aip.baidubce.com/oauth/2.0/token
 *         return-form: rgba       # rgba=透明四通道PNG / mask=二值遮罩
 *         refine-mask: true       # 边缘平滑
 *         method: auto            # auto=自动识别主体
 *       qwen:
 *         api-key: sk-xxx         # 留空则复用 trybe.ai.garment.api-key
 *         model: qwen-image-2.1-turbo
 *         prompt-extend: false    # 必须 false，否则「透明背景」约束会被改写掉
 * </pre>
 *
 * <p><b>为什么不用阿里云视觉智能开放平台的分割抠图</b>：该产品已公告退市
 * （2026-10-11 起停止新用户开通，2028-04-30 全面停止），新账号无法调用。
 *
 * <p><b>为什么不默认启用</b>：抠图接口按次计费。未显式配置时 {@code GarmentMattingService} Bean
 * 不会创建，导入自动走本地 bbox 裁剪，对现有流程零影响。
 */
@Data
@Component
@ConfigurationProperties(prefix = "trybe.ai.matting")
public class GarmentMattingProperties {

    /** 是否启用抠图；关闭后导入流程退化为「本地 bbox 矩形裁剪 / 原图」 */
    private Boolean enabled = false;

    /** 供应商标识：baidu（便宜、不重绘）/ qwen-image-edit（能点名品类、可能重绘） */
    private String provider = "baidu";

    /** 单次调用总超时（毫秒） */
    private Integer timeoutMs = 30000;

    /** 结果图体积上限（字节），超过即视为异常并降级，避免异常大图拖垮内存与 OSS */
    private Integer maxBytes = 5242880;

    /**
     * 白底产物长边上限（像素）
     *
     * <p>抠图服务按原图分辨率输出（电商图动辄 2000px+），而衣橱卡片只有 380rpx 高。
     * 合成白底时顺手等比压到这个尺寸：详情页够看，体积再砍一半。
     */
    private Integer whiteMaxSide = 1600;

    /** 百度智能云智能抠图 */
    private Baidu baidu = new Baidu();

    /** 阿里云百炼千问图像编辑 */
    private Qwen qwen = new Qwen();

    @Data
    public static class Baidu {

        /** 智能抠图接口地址 */
        private String baseUrl = "https://aip.baidubce.com/rest/2.0/image-process/v1/segment";

        /** access_token 获取地址 */
        private String tokenUrl = "https://aip.baidubce.com/oauth/2.0/token";

        /** 百度智能云应用 API Key */
        private String apiKey;

        /** 百度智能云应用 Secret Key（严禁写入日志） */
        private String secretKey;

        /** 返回形态：rgba=透明四通道 PNG / mask=二值遮罩 */
        private String returnForm = "rgba";

        /** 是否对边缘做平滑处理 */
        private Boolean refineMask = true;

        /** 抠图方式：auto=自动识别主体 / control=手动框选（需传 position） */
        private String method = "auto";
    }

    @Data
    public static class Qwen {

        /** 千问图像生成与编辑接口地址 */
        private String baseUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

        /** 异步任务查询地址前缀（模型返回 task_id 时轮询用） */
        private String taskUrl = "https://dashscope.aliyuncs.com/api/v1/tasks/";

        /** 百炼 API Key；留空则回落到 {@code trybe.ai.garment.api-key} */
        private String apiKey;

        /** 图像编辑模型名：qwen-image-2.1-turbo（快、便宜）/ qwen-image-2.1-pro（画质更好） */
        private String model = "qwen-image-2.1-turbo";

        /** 是否开启提示词智能改写。必须 false，否则透明背景约束会被稀释 */
        private Boolean promptExtend = false;

        /** 是否给结果打「Qwen-Image」水印 */
        private Boolean watermark = false;
    }
}
