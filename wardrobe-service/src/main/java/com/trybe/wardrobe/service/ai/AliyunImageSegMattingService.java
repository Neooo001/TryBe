package com.trybe.wardrobe.service.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.service.OssService;
import com.trybe.wardrobe.config.GarmentMattingProperties;
import com.trybe.wardrobe.util.WardrobeOssPathBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * 阿里云分割抠图 - 商品分割（SegmentCommodity）实现
 *
 * <h3>为什么不引 {@code aliyun-java-sdk-imageseg}，而是用 sdk-core 的通用 RPC 客户端</h3>
 * imageseg 的专用 SDK 需要额外下载一个不在本地仓库的 artifact，一旦在线仓库不可达就会让项目编译不过；
 * 而 {@code com.aliyun:aliyun-java-sdk-core} 已经是本项目既有依赖链中的常见成员（本地仓库已存在 4.5.10），
 * 用它的 {@link CommonRequest}/{@link CommonResponse} 通用 RPC 能力调任意 OpenAPI，
 * 零新增不可获取依赖，签名/编码/重试由官方 SDK 处理，最稳。
 *
 * <h3>调用要点</h3>
 * <ol>
 *   <li>RPC 风格的接口只吃「公网可访问的 ImageURL」，因此把原图对象生成一个<b>带签名的预签名 URL</b> 作为入参；
 *       该 URL 会被 SDK 作为 query 参数整体 URL 编码，签名里的 {@code + / =} 不会丢</li>
 *   <li>{@code ReturnForm=whiteBK} 直接返回纯白底前景图，正是「只把衣服抠出来、其余留白」的效果</li>
 *   <li>结果图 URL 下载后上传到 OSS 的抠图路径（经 {@link WardrobeOssPathBuilder}），返回新的 objectKey</li>
 * </ol>
 *
 * <p><b>安全</b>：全程不打印 AccessKeyId / AccessKeySecret；预签名 URL（含 OSS 签名）也不落日志，只打 objectKey。
 *
 * <p>通过 {@code trybe.ai.matting.enabled=true} 激活；未启用时本 Bean 不创建，导入自动走本地裁剪。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trybe.ai.matting.enabled", havingValue = "true")
public class AliyunImageSegMattingService implements GarmentMattingService {

    /** 分割抠图 OpenAPI 版本 */
    private static final String API_VERSION = "2019-12-30";
    /** 商品分割动作名 */
    private static final String API_ACTION = "SegmentCommodity";
    /** 预签名 URL 有效期（秒）：只需够云端服务在调用窗口内拉到图，取短一点更安全 */
    private static final long PRESIGN_TTL_SECONDS = 600L;
    /** 建立连接超时（毫秒） */
    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final GarmentMattingProperties properties;
    private final OssService ossService;

    /** 阿里云通用 RPC 客户端，懒初始化并复用（DefaultProfile 构造有一定开销） */
    private volatile IAcsClient client;

    @Override
    public String providerName() {
        return "aliyun-imageseg-segment-commodity";
    }

    @Override
    public String matting(String originObjectKey, Long userId, String taskNo) {
        if (StrUtil.isBlank(originObjectKey)) {
            throw new BizIllegalException(ErrorCodeEnum.PARAM_EMPTY, "原图为空，无法抠图");
        }

        // 1. 生成公网可访问的预签名 URL 作为接口入参
        String imageUrl = ossService.generatePresignedUrl(originObjectKey, PRESIGN_TTL_SECONDS);
        if (StrUtil.isBlank(imageUrl)) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "生成原图访问地址失败");
        }

        // 2. 调用商品分割
        long startMs = System.currentTimeMillis();
        String resultUrl = callSegmentCommodity(imageUrl);
        long costMs = System.currentTimeMillis() - startMs;

        // 3. 下载结果图
        byte[] bytes = download(resultUrl);

        // 4. 上传到抠图路径（必须走 PathBuilder，禁止手拼）
        String objectKey = WardrobeOssPathBuilder.importMatting(userId, taskNo);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            ossService.putObject(in, objectKey);
        } catch (Exception e) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果上传失败");
        }

        log.info("[GarmentMatting] 抠图成功 provider={} costMs={} bytes={} objectKey={}",
                providerName(), costMs, bytes.length, objectKey);
        return objectKey;
    }

    /**
     * 调用 SegmentCommodity，返回结果图 URL
     */
    private String callSegmentCommodity(String imageUrl) {
        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain(properties.getEndpoint());
        request.setSysVersion(API_VERSION);
        request.setSysAction(API_ACTION);
        // RegionId 用 SDK 标准入口下发：setSysRegionId 会把它并入签名的公共参数（sys 参数集），
        // 比手工 putQueryParameter("RegionId") 更规范，也避免与 SDK 自身的 region 处理重复下发。
        // 已确认 aliyun-java-sdk-core:4.5.10 的 CommonRequest 提供 setSysRegionId(String)。
        request.setSysRegionId(properties.getRegionId());
        request.putQueryParameter("ImageURL", imageUrl);
        request.putQueryParameter("ReturnForm", properties.getReturnForm());
        request.setSysConnectTimeout(CONNECT_TIMEOUT_MS);
        request.setSysReadTimeout(properties.getTimeoutMs() == null ? 10000 : properties.getTimeoutMs());

        try {
            CommonResponse response = client().getCommonResponse(request);
            if (response == null) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图服务无响应");
            }
            int status = response.getHttpStatus();
            String data = response.getData();
            if (status != 200) {
                // 只提取阿里云标准错误字段 Code / RequestId 落日志；
                // 响应体其余内容（可能回显入参、URL 等）一律不打印，杜绝凭据/敏感信息外泄。
                log.warn("[GarmentMatting] 调用返回非200 status={} code={} requestId={}",
                        status, extractField(data, "Code"), extractField(data, "RequestId"));
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图服务调用失败(" + status + ")");
            }
            String url = extractImageUrl(data);
            if (StrUtil.isBlank(url)) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果为空");
            }
            return url;
        } catch (BizIllegalException e) {
            throw e;
        } catch (Exception e) {
            // ServerException / ClientException 及网络异常统一降级
            log.warn("[GarmentMatting] 调用异常 provider={} err={}", providerName(), e.getMessage());
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图服务异常");
        }
    }

    /**
     * 从响应体取 Data.ImageURL（容错 null / 非 JSON）
     */
    private String extractImageUrl(String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        try {
            JSONObject root = JSONUtil.parseObj(body);
            JSONObject data = root.getJSONObject("Data");
            return data == null ? null : data.getStr("ImageURL");
        } catch (Exception e) {
            // 与调用失败分支（非200）保持同一脱敏口径：解析失败也只暴露 Code/RequestId，绝不打印响应体
            log.warn("[GarmentMatting] 返回体解析失败 code={} requestId={}",
                    extractField(body, "Code"), extractField(body, "RequestId"));
            return null;
        }
    }

    /**
     * 从响应体取指定顶层字段（容错 null / 非 JSON）。
     * 专供日志脱敏使用 —— 只取 {@code Code} / {@code RequestId} 这类非敏感字段，绝不整包打印响应体。
     */
    private String extractField(String body, String field) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(body).getStr(field);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 下载结果图字节（带体积上限保护）
     */
    private byte[] download(String url) {
        int maxBytes = properties.getMaxBytes() == null ? 5242880 : properties.getMaxBytes();
        int timeout = properties.getTimeoutMs() == null ? 10000 : properties.getTimeoutMs();
        try (HttpResponse response = HttpRequest.get(url).timeout(timeout).execute()) {
            if (!response.isOk()) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR,
                        "抠图结果下载失败(" + response.getStatus() + ")");
            }
            byte[] bytes = response.bodyBytes();
            if (bytes == null || bytes.length == 0) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果为空");
            }
            if (bytes.length > maxBytes) {
                throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果体积超限");
            }
            return bytes;
        } catch (BizIllegalException e) {
            throw e;
        } catch (Exception e) {
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "抠图结果下载异常");
        }
    }

    /**
     * 懒初始化阿里云通用 RPC 客户端；复用同一个实例
     */
    private IAcsClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    if (StrUtil.isBlank(properties.getAccessKeyId()) || StrUtil.isBlank(properties.getAccessKeySecret())) {
                        throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "未配置抠图服务 AccessKey");
                    }
                    DefaultProfile profile = DefaultProfile.getProfile(
                            properties.getRegionId(), properties.getAccessKeyId(), properties.getAccessKeySecret());
                    client = new DefaultAcsClient(profile);
                }
            }
        }
        return client;
    }
}
