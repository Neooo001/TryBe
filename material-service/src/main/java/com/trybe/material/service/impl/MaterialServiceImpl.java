package com.trybe.material.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.api.dto.BatchMaterialResult;
import com.trybe.api.dto.MaterialDTO;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.service.OssService;
import com.trybe.common.utils.BeanUtils;
import com.trybe.material.config.MaterialMqConfig;
import com.trybe.material.domain.dto.MaterialCheckMessage;
import com.trybe.material.domain.dto.PreSignUploadReq;
import com.trybe.material.domain.dto.UploadCallbackReq;
import com.trybe.material.domain.enums.MaterialStatus;
import com.trybe.material.domain.enums.MaterialType;
import com.trybe.material.domain.po.Material;
import com.trybe.material.domain.vo.MaterialVO;
import com.trybe.material.domain.vo.PreSignUploadVO;
import com.trybe.material.mapper.MaterialCheckLogMapper;
import com.trybe.material.mapper.MaterialMapper;
import com.trybe.material.service.IMaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 素材服务实现
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, Material> implements IMaterialService {

    private final OssService ossService;
    private final RabbitTemplate rabbitTemplate;
    private final MaterialCheckLogMapper materialCheckLogMapper;

    private static final String MATERIAL_TMP_PATH_PREFIX = "material/tmp";

    @Override
    @Transactional
    public PreSignUploadVO generatePreSignUploadUrl(Long userId, PreSignUploadReq req) {
        MaterialType materialType = MaterialType.of(req.getMaterialType());

        // 1. 创建素材记录，状态为待上传
        Material material = new Material();
        material.setUserId(userId);
        material.setMaterialType(materialType);
        material.setFileName(req.getFileName());
        material.setFileSize(req.getFileSize());
        material.setContentType(req.getContentType());
        material.setStatus(MaterialStatus.PENDING_UPLOAD);
        save(material);

        // 2. 构建OSS临时路径: material/tmp/{userId}/{materialType}/{materialId}.{ext}
        // 校验通过后移动到 material/{userId}/{materialType}/{materialId}.{ext}
        String extension = StrUtil.subAfter(req.getFileName(), ".", true);
        if (StrUtil.isBlank(extension)) {
            extension = "jpg";
        }
        String objectKey = String.format("%s/%d/%s/%d.%s",
                MATERIAL_TMP_PATH_PREFIX, userId, materialType.getValue().toLowerCase(), material.getId(), extension);
        material.setObjectKey(objectKey);
        updateById(material);

        // 3. 生成预签名上传URL（Content-Type 参与签名，须与客户端 PUT 一致）
        String uploadUrl = ossService.generatePresignedPutUrl(objectKey, req.getContentType());

        log.info("[Material] 生成预签名上传URL userId={} materialId={} objectKey={}", userId, material.getId(), objectKey);

        return PreSignUploadVO.builder()
                .materialId(material.getId())
                .uploadUrl(uploadUrl)
                .objectKey(objectKey)
                .expireSeconds(3600L)
                .build();
    }

    @Override
    @Transactional
    public void uploadCallback(Long userId, UploadCallbackReq req) {
        Long materialId = req.getMaterialId();
        Material material = getById(materialId);

        // 1. 数据校验
        if (material == null) {
            throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "素材不存在");
        }
        if (!material.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, "无权操作该素材");
        }

        // 2. 幂等校验：只有待上传状态才允许回调
        if (material.getStatus() != MaterialStatus.PENDING_UPLOAD) {
            log.warn("[Material] 重复回调 materialId={} currentStatus={}", materialId, material.getStatus());
            return;
        }

        // 3. 更新状态为校验中
        material.setStatus(MaterialStatus.CHECKING);
        material.setUpdateTime(LocalDateTime.now());
        updateById(material);

        log.info("[Material] 上传回调处理完成 materialId={} userId={}", materialId, userId);

        // 4. 发送MQ消息触发异步校验（事务提交后再发，避免消费者读到未提交状态）
        final MaterialCheckMessage message = new MaterialCheckMessage(materialId, IdUtil.fastSimpleUUID());
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            rabbitTemplate.convertAndSend(MaterialMqConfig.CHECK_EXCHANGE,
                                    MaterialMqConfig.CHECK_ROUTING_KEY, message);
                            log.info("[Material] 已发送校验消息 materialId={} messageId={}",
                                    materialId, message.getMessageId());
                        }
                    });
        } else {
            // 无事务上下文兜底（理论上不会走到）
            rabbitTemplate.convertAndSend(MaterialMqConfig.CHECK_EXCHANGE, MaterialMqConfig.CHECK_ROUTING_KEY, message);
            log.info("[Material] 已发送校验消息 materialId={} messageId={}", materialId, message.getMessageId());
        }
    }

    @Override
    public MaterialVO getMaterialDetail(Long userId, Long materialId) {
        Material material = getById(materialId);
        if (material == null) {
            throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "素材不存在");
        }
        if (!material.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, "无权访问该素材");
        }

        MaterialVO vo = BeanUtils.copyBean(material, MaterialVO.class, (s, t) -> {
            t.setMaterialId(s.getId());
            t.setMaterialType(s.getMaterialType().getValue());
            t.setStatus(s.getStatus().getValue());
        });

        // 校验通过的素材生成临时访问URL
        if (material.getStatus() == MaterialStatus.PASSED) {
            vo.setAccessUrl(ossService.generatePresignedUrl(material.getObjectKey()));
        }

        return vo;
    }

    @Override
    public PageDTO<MaterialVO> listUserMaterials(Long userId, String materialType, PageQuery pageQuery) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<Material>()
                .eq(Material::getUserId, userId)
                .orderByDesc(Material::getCreateTime);

        if (StrUtil.isNotBlank(materialType)) {
            wrapper.eq(Material::getMaterialType, MaterialType.of(materialType));
        }

        Page<Material> page = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        page = baseMapper.selectPage(page, wrapper);

        return PageDTO.of(page, m -> {
            MaterialVO vo = BeanUtils.copyBean(m, MaterialVO.class, (s, t) -> {
                t.setMaterialId(s.getId());
                t.setMaterialType(s.getMaterialType().getValue());
                t.setStatus(s.getStatus().getValue());
            });
            if (m.getStatus() == MaterialStatus.PASSED) {
                vo.setAccessUrl(ossService.generatePresignedUrl(m.getObjectKey()));
            }
            return vo;
        });
    }

    @Override
    @Transactional
    public void deleteMaterial(Long userId, Long materialId) {
        Material material = getById(materialId);
        if (material == null) {
            throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "素材不存在");
        }
        if (!material.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, "无权操作该素材");
        }

        // 刷新 update_time 作为"删除时刻"基准（逻辑删除不会自动更新时间戳），
        // 这样 7 天归档保留期从本次删除起算，保证误删可恢复。
        material.setUpdateTime(LocalDateTime.now());
        updateById(material);

        // 软删除数据库记录（逻辑删除）。OSS 文件不立即删除，
        // 交由定时任务 physicallyClean 在归档保留期后一并清理。
        removeById(materialId);

        log.info("[Material] 素材已软删除 materialId={} userId={}", materialId, userId);
    }

    // ==================== 垃圾回收方法 ====================

    @Override
    @Transactional
    public void markFailed(Long materialId, String failReason) {
        Material material = getById(materialId);
        if (material == null) {
            return;
        }
        // 标记失败状态（不立即软删，前端需查询失败原因；软删交由定时任务延迟处理）
        material.setStatus(MaterialStatus.FAILED);
        material.setFailReason(failReason);
        material.setUpdateTime(LocalDateTime.now());
        updateById(material);

        // 删除临时文件（校验失败的素材只存在于 tmp 前缀）
        deleteTmpFile(material.getObjectKey());
        log.info("[Material] 校验失败已标记 materialId={} failReason={}", materialId, failReason);
    }

    @Override
    @Transactional
    public int cleanZombie(LocalDateTime beforeTime) {
        List<Material> zombies = lambdaQuery()
                .eq(Material::getStatus, MaterialStatus.PENDING_UPLOAD)
                .lt(Material::getCreateTime, beforeTime)
                .list();

        int cleaned = 0;
        for (Material material : zombies) {
            removeById(material.getId());
            deleteTmpFile(material.getObjectKey());
            cleaned++;
        }
        if (cleaned > 0) {
            log.info("[Material] 清理上传僵尸素材 count={}", cleaned);
        }
        return cleaned;
    }

    @Override
    @Transactional
    public int archiveFailed(LocalDateTime beforeTime) {
        List<Material> failed = lambdaQuery()
                .eq(Material::getStatus, MaterialStatus.FAILED)
                .lt(Material::getUpdateTime, beforeTime)
                .list();

        int archived = 0;
        for (Material material : failed) {
            // 失败素材的 tmp 文件在 markFailed 时已删，这里只需软删记录
            removeById(material.getId());
            archived++;
        }
        if (archived > 0) {
            log.info("[Material] 归档失败素材 count={}", archived);
        }
        return archived;
    }

    @Override
    @Transactional
    public int physicallyClean(LocalDateTime beforeTime) {
        List<Material> deleted = baseMapper.selectDeletedBefore(beforeTime);

        int cleaned = 0;
        for (Material material : deleted) {
            // 删除正式文件（软删记录可能还留有正式路径文件）
            deleteFileQuietly(material.getObjectKey());
            // 删除校验日志
            materialCheckLogMapper.deleteByMaterialId(material.getId());
            // 物理删除记录
            baseMapper.physicallyDeleteById(material.getId());
            cleaned++;
        }
        if (cleaned > 0) {
            log.info("[Material] 归档物理清理 count={}", cleaned);
        }
        return cleaned;
    }

    private void deleteTmpFile(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            return;
        }
        deleteFileQuietly(objectKey);
    }

    private void deleteFileQuietly(String objectKey) {
        try {
            ossService.deleteFile(objectKey);
        } catch (Exception e) {
            log.warn("[Material] OSS文件删除失败 objectKey={}", objectKey, e);
        }
    }

    // ==================== Feign 服务间调用方法 ====================

    @Override
    public BatchMaterialResult validateMaterials(Long userId, List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return new BatchMaterialResult(Collections.emptyList(), Collections.emptyList());
        }

        List<Material> materials = lambdaQuery()
                .in(Material::getId, materialIds)
                .eq(Material::getUserId, userId)
                .eq(Material::getStatus, MaterialStatus.PASSED)
                .list();

        List<Long> validIds = materials.stream().map(Material::getId).collect(Collectors.toList());
        List<Long> invalidIds = materialIds.stream()
                .filter(id -> !validIds.contains(id))
                .collect(Collectors.toList());

        List<MaterialDTO> validMaterials = materials.stream().map(m ->
                new MaterialDTO(m.getId(), m.getUserId(), m.getMaterialType().getValue(),
                        m.getObjectKey(), m.getStatus().getValue(), m.getFailReason(), null)
        ).collect(Collectors.toList());

        log.info("[Material] 批量校验材料有效性 userId={} total={} valid={} invalid={}",
                userId, materialIds.size(), validIds.size(), invalidIds.size());
        return new BatchMaterialResult(validMaterials, invalidIds);
    }

    @Override
    public BatchMaterialResult getMaterialAccessUrls(Long userId, List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return new BatchMaterialResult(Collections.emptyList(), Collections.emptyList());
        }

        List<Material> materials = lambdaQuery()
                .in(Material::getId, materialIds)
                .eq(Material::getUserId, userId)
                .eq(Material::getStatus, MaterialStatus.PASSED)
                .list();

        List<Long> validIds = materials.stream().map(Material::getId).collect(Collectors.toList());
        List<Long> invalidIds = materialIds.stream()
                .filter(id -> !validIds.contains(id))
                .collect(Collectors.toList());

        List<MaterialDTO> validMaterials = materials.stream().map(m -> {
            String accessUrl = ossService.generatePresignedUrl(m.getObjectKey());
            return new MaterialDTO(m.getId(), m.getUserId(), m.getMaterialType().getValue(),
                    m.getObjectKey(), m.getStatus().getValue(), m.getFailReason(), accessUrl);
        }).collect(Collectors.toList());

        log.info("[Material] 批量获取材料访问链接 userId={} total={} valid={} invalid={}",
                userId, materialIds.size(), validIds.size(), invalidIds.size());
        return new BatchMaterialResult(validMaterials, invalidIds);
    }
}