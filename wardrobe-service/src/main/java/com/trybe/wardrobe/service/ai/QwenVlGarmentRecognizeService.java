package com.trybe.wardrobe.service.ai;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.wardrobe.config.GarmentAiProperties;
import com.trybe.wardrobe.domain.dto.GarmentRecognizeResult;
import com.trybe.wardrobe.domain.enums.GarmentCategoryL1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云百炼 Qwen-VL 衣服识别实现
 *
 * <p>走百炼的 OpenAI 兼容接口（/compatible-mode/v1/chat/completions），
 * 单次调用同时产出三类结果：
 * <ol>
 *   <li>结构化属性 — 品类、二级品类、颜色、季节、风格、材质、场合、版型、厚薄、保暖度、正式度</li>
 *   <li>主体框 bbox（归一化 0-1）— 供服务端本地裁剪，无需再调用分割 API</li>
 *   <li>AI 生成标题与标签 — 直接用作衣橱条目标题</li>
 * </ol>
 *
 * <p>为什么不做 response_format=json_object：不同 Qwen-VL 版本对该参数支持不一致，
 * 强传可能触发 400。改为「提示词约束 + 括号配对提取」的容错解析，兼容性更好。
 *
 * <p>通过 {@code trybe.ai.garment.provider=qwen-vl} 激活，方便后续替换其它供应商实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trybe.ai.garment.provider", havingValue = "qwen-vl", matchIfMissing = true)
public class QwenVlGarmentRecognizeService implements GarmentRecognizeService {

    private static final String SYSTEM_PROMPT =
            "你是服装电商领域的视觉识别专家，擅长从图片中精准定位并描述单件服装商品。"
                    + "你的输出必须是严格的 JSON，不得包含任何解释性文字或 Markdown 代码块标记。";

    /** 提示词模板：把分类字典注入给模型，显著降低自由发挥导致的归类漂移 */
    private static final String USER_PROMPT_TEMPLATE =
            "请识别这张图片中【最主要的一件服装】（若出现模特，请忽略人体与背景，只关注那件服装本身）。\n"
                    + "\n"
                    + "【一级分类对应关系，categoryL1 必须从以下代码中选择】\n"
                    + "TOP=上装, BOTTOM=下装, SET=套装, SHOES=鞋子, BAG=包包, "
                    + "ACCESSORY=首饰, DECOR=配饰, OTHER=无法判断\n"
                    + "【二级分类参考，可自行归纳为最贴近的一个词】\n"
                    + "上装：外套/短袖/卫衣/毛衣/背心/打底；下装：长裤/短裤/长裙/短裙/打底裤；"
                    + "套装：连衣裙/连裤；鞋子：运动鞋/休闲鞋/凉鞋/拖鞋/高跟鞋/靴子/皮鞋\n"
                    + "\n"
                    + "【bbox 说明】x1,y1 为服装区域左上角，x2,y2 为右下角，"
                    + "均使用相对整张图片宽高的归一化比例（0 到 1 之间，保留 3 位小数）。"
                    + "必须紧贴该服装外轮廓，不要把整张图片全部框进去。\n"
                    + "\n"
                    + "严格按以下 JSON 结构返回，字段不可缺失，无法判断的填 null：\n"
                    + "{\n"
                    + "  \"categoryL1\": \"BOTTOM\",\n"
                    + "  \"categoryL2\": \"长裤\",\n"
                    + "  \"title\": \"灰色直筒休闲长裤\",\n"
                    + "  \"color\": \"灰色\",\n"
                    + "  \"colorHex\": \"#6E6E6E\",\n"
                    + "  \"colors\": [\"灰色\"],\n"
                    + "  \"seasons\": [\"SPRING\", \"AUTUMN\", \"WINTER\"],\n"
                    + "  \"style\": \"简约通勤\",\n"
                    + "  \"styles\": [\"简约\", \"通勤\"],\n"
                    + "  \"material\": \"棉质\",\n"
                    + "  \"occasions\": [\"通勤\", \"日常\"],\n"
                    + "  \"fit\": \"直筒\",\n"
                    + "  \"pattern\": \"纯色\",\n"
                    + "  \"thickness\": \"MEDIUM\",\n"
                    + "  \"warmth\": 3,\n"
                    + "  \"formality\": 3,\n"
                    + "  \"tags\": [\"基础款\", \"百搭\"],\n"
                    + "  \"confidence\": 0.92,\n"
                    + "  \"bbox\": {\"x1\": 0.180, \"y1\": 0.060, \"x2\": 0.820, \"y2\": 0.980}\n"
                    + "}\n"
                    + "\n"
                    + "约束：seasons 只能取 SPRING/SUMMER/AUTUMN/WINTER 中的值；"
                    + "thickness 只能取 THIN/MEDIUM/THICK；warmth 与 formality 为 1-5 的整数；"
                    + "confidence 为 0-1 的小数。";

    private final GarmentAiProperties properties;

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public GarmentRecognizeResult recognize(byte[] imageBytes, String contentType) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BizIllegalException(ErrorCodeEnum.PARAM_EMPTY, "图片内容为空");
        }
        if (StrUtil.isBlank(properties.getApiKey())) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "未配置衣服识别模型 API Key");
        }

        String mime = StrUtil.isBlank(contentType) ? "image/jpeg" : contentType;
        String dataUrl = "data:" + mime + ";base64," + Base64.encode(imageBytes);
        String body = buildRequestBody(dataUrl);

        long startMs = System.currentTimeMillis();
        String responseBody;
        try (HttpResponse response = HttpRequest.post(properties.getBaseUrl())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(properties.getTimeoutMs() == null ? 30000 : properties.getTimeoutMs())
                .body(body)
                .execute()) {

            responseBody = response.body();
            if (!response.isOk()) {
                log.error("[GarmentAI] 调用失败 status={} body={}", response.getStatus(), StrUtil.maxLength(responseBody, 500));
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR,
                        "衣服识别服务调用失败(" + response.getStatus() + ")");
            }
        } catch (BizIllegalException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GarmentAI] 调用异常 model={}", properties.getModel(), e);
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "衣服识别服务异常，请稍后重试");
        }

        long costMs = System.currentTimeMillis() - startMs;
        String content = extractContent(responseBody);
        log.info("[GarmentAI] 识别完成 model={} costMs={} contentLength={}",
                properties.getModel(), costMs, StrUtil.length(content));

        GarmentRecognizeResult result = parse(content);
        result.setRawJson(responseBody);
        return result;
    }

    /**
     * 组装 OpenAI 兼容请求体（文本 + 图片多模态消息）
     *
     * <p>注意：JSONArray 的 add 来自 List 接口、返回 boolean，不能链式调用，
     * 因此这里分行写，避免依赖 hutool 不同版本的方法签名差异。
     */
    private String buildRequestBody(String imageDataUrl) {
        JSONObject textPart = new JSONObject();
        textPart.set("type", "text");
        textPart.set("text", USER_PROMPT_TEMPLATE);

        JSONObject imageUrl = new JSONObject();
        imageUrl.set("url", imageDataUrl);

        JSONObject imagePart = new JSONObject();
        imagePart.set("type", "image_url");
        imagePart.set("image_url", imageUrl);

        JSONArray content = new JSONArray();
        content.add(textPart);
        content.add(imagePart);

        JSONObject systemMessage = new JSONObject();
        systemMessage.set("role", "system");
        systemMessage.set("content", SYSTEM_PROMPT);

        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", content);

        JSONArray messages = new JSONArray();
        messages.add(systemMessage);
        messages.add(userMessage);

        JSONObject payload = new JSONObject();
        payload.set("model", properties.getModel());
        payload.set("messages", messages);
        payload.set("temperature", 0.1);
        payload.set("max_tokens", 1200);
        payload.set("stream", false);

        return payload.toString();
    }

    /**
     * 从 OpenAI 兼容响应中取出 choices[0].message.content
     */
    private String extractContent(String responseBody) {
        try {
            JSONObject root = JSONUtil.parseObj(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "识别服务返回内容为空");
            }
            String content = choices.getJSONObject(0).getJSONObject("message").getStr("content");
            if (StrUtil.isBlank(content)) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "识别服务返回内容为空");
            }
            return content;
        } catch (BizIllegalException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GarmentAI] 响应解析失败 body={}", StrUtil.maxLength(responseBody, 500), e);
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "识别服务响应格式异常");
        }
    }

    /**
     * 容错解析模型输出：剥离 Markdown 代码块 / 前后缀说明文字，取最外层 JSON 对象
     */
    private GarmentRecognizeResult parse(String content) {
        String json = extractJsonObject(content);
        JSONObject obj;
        try {
            obj = JSONUtil.parseObj(json);
        } catch (Exception e) {
            log.error("[GarmentAI] 模型输出非合法JSON content={}", StrUtil.maxLength(content, 500), e);
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "识别结果解析失败，请重试");
        }

        GarmentRecognizeResult result = new GarmentRecognizeResult();
        GarmentCategoryL1 l1 = GarmentCategoryL1.of(obj.getStr("categoryL1"));
        result.setCategoryL1(l1.getCode());
        result.setCategoryL1Name(l1.getDesc());

        String l2 = obj.getStr("categoryL2");
        result.setCategoryL2(StrUtil.isBlank(l2) ? "未分类" : l2.trim());
        result.setCategoryName(l1.getDesc() + "-" + result.getCategoryL2());

        result.setTitle(obj.getStr("title"));
        result.setColor(obj.getStr("color"));
        result.setColorHex(normalizeHex(obj.getStr("colorHex")));
        result.setColors(stringList(obj, "colors"));
        result.setSeasons(normalizeSeasons(stringList(obj, "seasons")));
        result.setStyle(obj.getStr("style"));
        result.setStyles(stringList(obj, "styles"));
        result.setMaterial(obj.getStr("material"));
        result.setOccasions(stringList(obj, "occasions"));
        result.setFit(obj.getStr("fit"));
        result.setPattern(obj.getStr("pattern"));
        result.setThickness(normalizeThickness(obj.getStr("thickness")));
        result.setWarmth(clampInt(obj.getInt("warmth"), 1, 5));
        result.setFormality(clampInt(obj.getInt("formality"), 1, 5));
        result.setTags(stringList(obj, "tags"));
        result.setConfidence(clampDouble(obj.getDouble("confidence")));
        result.setBbox(parseBbox(obj.getJSONObject("bbox")));
        return result;
    }

    private GarmentRecognizeResult.Bbox parseBbox(JSONObject bboxObj) {
        if (bboxObj == null) {
            return null;
        }
        GarmentRecognizeResult.Bbox bbox = new GarmentRecognizeResult.Bbox();
        bbox.setX1(bboxObj.getDouble("x1"));
        bbox.setY1(bboxObj.getDouble("y1"));
        bbox.setX2(bboxObj.getDouble("x2"));
        bbox.setY2(bboxObj.getDouble("y2"));
        if (!bbox.valid()) {
            log.warn("[GarmentAI] bbox 非法，跳过裁剪 x1={} y1={} x2={} y2={}",
                    bbox.getX1(), bbox.getY1(), bbox.getX2(), bbox.getY2());
            return null;
        }
        return bbox;
    }

    /**
     * 从模型输出中提取最外层 JSON 对象 —— 用括号计数而非正则，避免嵌套花括号截断
     */
    static String extractJsonObject(String content) {
        if (content == null) {
            return "";
        }
        String text = content.trim();
        // 剥离 ```json ... ``` 代码块
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd > 0) {
                text = text.substring(0, fenceEnd);
            }
            text = text.trim();
        }

        int start = text.indexOf('{');
        if (start < 0) {
            return text;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return text.substring(start);
    }

    private List<String> stringList(JSONObject obj, String key) {
        JSONArray array = obj.getJSONArray(key);
        if (array == null || array.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        for (Object item : array) {
            if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                list.add(String.valueOf(item).trim());
            }
        }
        return list;
    }

    /** 季节码归一：中文与大小写都映射为标准码，去重并保持固定顺序 */
    private List<String> normalizeSeasons(List<String> raw) {
        List<String> ordered = new ArrayList<>();
        for (String code : new String[]{"SPRING", "SUMMER", "AUTUMN", "WINTER"}) {
            for (String item : raw) {
                String upper = item.toUpperCase();
                if (upper.equals(code) || upper.contains(code)
                        || ("SPRING".equals(code) && item.contains("春"))
                        || ("SUMMER".equals(code) && item.contains("夏"))
                        || ("AUTUMN".equals(code) && (item.contains("秋") || upper.contains("FALL")))
                        || ("WINTER".equals(code) && item.contains("冬"))) {
                    ordered.add(code);
                    break;
                }
            }
        }
        return ordered;
    }

    private String normalizeThickness(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        if (upper.contains("THIN") || value.contains("薄")) {
            return "THIN";
        }
        if (upper.contains("THICK") || value.contains("厚")) {
            return "THICK";
        }
        return "MEDIUM";
    }

    private String normalizeHex(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String hex = value.trim();
        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }
        return hex.matches("#[0-9a-fA-F]{6}") ? hex.toUpperCase() : null;
    }

    private Integer clampInt(Integer value, int min, int max) {
        if (value == null) {
            return null;
        }
        return Math.max(min, Math.min(max, value));
    }

    private Double clampDouble(Double value) {
        if (value == null) {
            return null;
        }
        return Math.max(0D, Math.min(1D, value));
    }
}
