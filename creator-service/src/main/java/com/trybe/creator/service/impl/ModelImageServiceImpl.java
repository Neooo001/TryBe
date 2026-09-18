package com.trybe.creator.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.service.FaceDetectResult;
import com.trybe.common.service.FaceDetectService;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.BeanUtils;
import com.trybe.common.utils.CollUtils;
import com.trybe.common.utils.ImageUtils;
import com.trybe.creator.domain.dto.ModelImageBindReq;
import com.trybe.creator.domain.enums.QcStatus;
import com.trybe.creator.domain.po.CreatorItem;
import com.trybe.creator.domain.po.CreatorItemModelImage;
import com.trybe.creator.domain.vo.ModelImageVO;
import com.trybe.creator.mapper.CreatorItemMapper;
import com.trybe.creator.mapper.CreatorItemModelImageMapper;
import com.trybe.creator.service.IModelImageService;
import com.trybe.creator.util.CreatorOssPathBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模特图服务实现
 *
 * <p>本类涉及 OSS 读取与人脸检测（外部交互），按红线① <b>不加 @Transactional</b>：
 * 先落库（PENDING），再同步质检并回写结果；质检异常降级为 WARN，不回滚、不影响主流程。
 *
 * @author TryBe Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelImageServiceImpl extends ServiceImpl<CreatorItemModelImageMapper, CreatorItemModelImage>
        implements IModelImageService {

    /** 尺寸下限（px） */
    private static final int MIN_SIDE = 512;
    /** 宽高比下限（w/h，竖版） */
    private static final double MIN_ASPECT_RATIO = 0.5D;
    /** 宽高比上限 */
    private static final double MAX_ASPECT_RATIO = 0.8D;
    /** 期望人脸数量 */
    private static final int EXPECTED_FACE_COUNT = 1;

    private final CreatorItemMapper creatorItemMapper;
    private final OssService ossService;
    private final FaceDetectService faceDetectService;

    /**
     * M1 绑定模特图 + 同步质检（外部交互，无事务）
     */
    @Override
    public ModelImageVO bind(Long creatorId, Long itemId, ModelImageBindReq req) {
        CreatorItem item = creatorItemMapper.selectById(itemId);
        if (item == null || item.getCreatorId() == null || !item.getCreatorId().equals(creatorId)) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "物品不存在");
        }
        if (!CreatorOssPathBuilder.belongsTo(creatorId, req.getImageKey())) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, "无权使用该图片");
        }
        if (!Boolean.TRUE.equals(req.getAuthConfirmed())) {
            throw new BadRequestException("请先确认肖像授权声明");
        }

        CreatorItemModelImage image = new CreatorItemModelImage()
                .setItemId(itemId)
                .setCreatorId(creatorId)
                .setImageKey(req.getImageKey())
                .setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder())
                .setAuthConfirmed(1)
                .setAuthVersion(req.getAuthVersion())
                .setAuthConfirmedAt(LocalDateTime.now())
                .setQcStatus(QcStatus.PENDING.getCode());
        save(image);
        log.info("[modelImage] 模特图已绑定 itemId={} imageId={} creatorId={}", itemId, image.getId(), creatorId);

        // 同步质检：失败静默降级，不阻断保存
        qualityCheck(image);
        return toVO(image);
    }

    /**
     * M2 我的物品模特图列表：先校验物品归属，非本人返回 402
     */
    @Override
    public List<ModelImageVO> listMyItemImages(Long creatorId, Long itemId) {
        CreatorItem item = creatorItemMapper.selectById(itemId);
        if (item == null || item.getCreatorId() == null || !item.getCreatorId().equals(creatorId)) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "物品不存在");
        }
        return listByItem(itemId);
    }

    @Override
    public List<ModelImageVO> listByItem(Long itemId) {
        List<CreatorItemModelImage> images = list(new LambdaQueryWrapper<CreatorItemModelImage>()
                .eq(CreatorItemModelImage::getItemId, itemId)
                .orderByAsc(CreatorItemModelImage::getSortOrder)
                .orderByAsc(CreatorItemModelImage::getId));
        if (CollUtils.isEmpty(images)) {
            return CollUtils.emptyList();
        }
        return BeanUtils.copyList(images, ModelImageVO.class, (source, target) ->
                target.setImageUrl(ossService.generatePresignedUrl(source.getImageKey())));
    }

    @Override
    public void deleteImage(Long creatorId, Long imageId) {
        boolean removed = remove(new LambdaQueryWrapper<CreatorItemModelImage>()
                .eq(CreatorItemModelImage::getId, imageId)
                .eq(CreatorItemModelImage::getCreatorId, creatorId));
        if (!removed) {
            throw new BizIllegalException(ErrorCodeEnum.DATA_NOT_FOUND, "模特图不存在");
        }
        log.info("[modelImage] 模特图已删除 imageId={} creatorId={}", imageId, creatorId);
    }

    @Override
    public long countAuthorized(Long itemId) {
        return count(new LambdaQueryWrapper<CreatorItemModelImage>()
                .eq(CreatorItemModelImage::getItemId, itemId)
                .eq(CreatorItemModelImage::getAuthConfirmed, 1));
    }

    // ==================== 私有：质检 ====================

    /**
     * 同步质检：尺寸下限 → 宽高比 → 人脸数
     *
     * <p>降级约定：
     * <ul>
     *   <li>尺寸/比例不过 → FAIL</li>
     *   <li>人脸数不符或人脸服务异常 → WARN（仍可用于展示，仅提示）</li>
     * </ul>
     * 任何异常都吞掉并落 qcDetail，绝不向上抛出导致保存失败。
     */
    private void qualityCheck(CreatorItemModelImage image) {
        JSONObject detail = JSONUtil.createObj();
        try {
            byte[] bytes = readOssBytes(image.getImageKey());
            BufferedImage buffered = ImageUtils.read(bytes);
            if (buffered == null) {
                degrade(image, QcStatus.WARN, detail.set("error", "图片无法解码"));
                return;
            }
            int width = buffered.getWidth();
            int height = buffered.getHeight();
            image.setWidth(width);
            image.setHeight(height);

            boolean minSizeOk = width >= MIN_SIDE && height >= MIN_SIDE;
            double ratio = height == 0 ? 0D : (double) width / height;
            boolean ratioOk = ratio >= MIN_ASPECT_RATIO && ratio <= MAX_ASPECT_RATIO;

            detail.set("width", width)
                    .set("height", height)
                    .set("minSizeOk", minSizeOk)
                    .set("aspectRatio", Math.round(ratio * 1000.0) / 1000.0)
                    .set("aspectRatioOk", ratioOk);

            if (!minSizeOk || !ratioOk) {
                detail.set("reason", "图片尺寸或宽高比不满足要求（≥512×512、宽高比0.5~0.8）");
                updateQc(image, QcStatus.FAIL, detail);
                return;
            }

            FaceDetectResult detect = faceDetectService.detect(new ByteArrayInputStream(bytes));
            int faceCount = detect == null ? -1 : detect.getFaceCount();
            boolean faceOk = detect != null && detect.isSuccess() && faceCount == EXPECTED_FACE_COUNT;
            detail.set("faceCount", faceCount)
                    .set("faceOk", faceOk)
                    .set("detectSuccess", detect != null && detect.isSuccess());

            if (faceOk) {
                updateQc(image, QcStatus.PASS, detail);
            } else {
                detail.set("reason", "未检测到恰好 1 张人脸");
                updateQc(image, QcStatus.WARN, detail);
            }
        } catch (Exception e) {
            // 红线⑥：质检链路整体静默降级
            log.warn("[modelImage] 模特图质检异常，降级为 WARN imageId={}", image.getId(), e);
            degrade(image, QcStatus.WARN, JSONUtil.createObj().set("error", "质检服务不可用"));
        }
    }

    private void degrade(CreatorItemModelImage image, QcStatus status, JSONObject detail) {
        try {
            updateQc(image, status, detail);
        } catch (Exception e) {
            log.warn("[modelImage] 回写质检结果失败 imageId={}", image.getId(), e);
        }
    }

    private void updateQc(CreatorItemModelImage image, QcStatus status, JSONObject detail) {
        image.setQcStatus(status.getCode());
        image.setQcDetail(detail.toString());
        updateById(image);
    }

    private byte[] readOssBytes(String objectKey) throws IOException {
        try (InputStream in = ossService.getObjectContent(objectKey)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = in.read(data)) != -1) {
                buffer.write(data, 0, n);
            }
            return buffer.toByteArray();
        }
    }

    private ModelImageVO toVO(CreatorItemModelImage image) {
        ModelImageVO vo = BeanUtils.copyBean(image, ModelImageVO.class);
        vo.setImageUrl(ossService.generatePresignedUrl(image.getImageKey()));
        return vo;
    }
}
