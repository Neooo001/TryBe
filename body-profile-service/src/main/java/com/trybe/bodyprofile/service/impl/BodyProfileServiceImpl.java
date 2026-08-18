package com.trybe.bodyprofile.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.api.client.MaterialClient;
import com.trybe.api.dto.BatchMaterialResult;
import com.trybe.api.dto.MaterialDTO;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BadRequestException;
import com.trybe.bodyprofile.domain.dto.UpsertProfileReq;
import com.trybe.bodyprofile.domain.enums.BodyType;
import com.trybe.bodyprofile.domain.po.BodyProfile;
import com.trybe.bodyprofile.domain.po.BodyProfileMaterial;
import com.trybe.bodyprofile.domain.vo.BodyProfileVO;
import com.trybe.bodyprofile.domain.vo.ProfileMaterialVO;
import com.trybe.bodyprofile.mapper.BodyProfileMapper;
import com.trybe.bodyprofile.mapper.BodyProfileMaterialMapper;
import com.trybe.bodyprofile.service.IBodyProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 身形档案服务实现 — 单档案 + 素材中间表关联
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BodyProfileServiceImpl extends ServiceImpl<BodyProfileMapper, BodyProfile> implements IBodyProfileService {

    private final BodyProfileMaterialMapper materialMapper;
    private final MaterialClient materialClient;

    private static final String DEFAULT_PROFILE_NAME = "默认档案";

    @Override
    public BodyProfileVO getMyProfile(Long userId) {
        BodyProfile profile = selectByUserId(userId);
        if (profile == null) {
            return null;
        }
        return toVO(profile, listMaterials(profile.getId(), userId));
    }

    @Override
    @Transactional
    public BodyProfileVO upsertBody(Long userId, UpsertProfileReq req) {
        BodyProfile profile = getOrCreate(userId);
        applyBodyData(profile, req);
        profile.setUpdateTime(LocalDateTime.now());
        updateById(profile);

        log.info("[BodyProfile] 身型档案已保存 userId={} profileId={}", userId, profile.getId());
        return toVO(profile, listMaterials(profile.getId(), userId));
    }

    @Override
    @Transactional
    public BodyProfileVO linkMaterial(Long userId, Long materialId) {
        if (materialId == null) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "素材ID不能为空");
        }

        // 1. 校验素材有效性并确定类型
        BatchMaterialResult result = materialClient.validateMaterials(userId, Collections.singletonList(materialId));
        if (result.getInvalidMaterialIds() != null && !result.getInvalidMaterialIds().isEmpty()) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_FORMAT_ERR,
                    "素材无效或未通过校验: " + materialId);
        }
        List<MaterialDTO> validMaterials = result.getValidMaterials();
        if (validMaterials == null || validMaterials.isEmpty()) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_FORMAT_ERR, "素材无效或未通过校验: " + materialId);
        }
        String materialType = validMaterials.get(0).getMaterialType();

        // 2. 懒创建档案
        BodyProfile profile = getOrCreate(userId);

        // 3. 幂等：已关联则直接返回
        Integer count = selectCount(profile.getId(), materialId);
        if (count != null && count > 0) {
            log.info("[BodyProfile] 素材已关联，跳过 userId={} materialId={}", userId, materialId);
            return toVO(profile, listMaterials(profile.getId(), userId));
        }

        // 4. 写入中间表
        BodyProfileMaterial link = new BodyProfileMaterial();
        link.setProfileId(profile.getId());
        link.setMaterialId(materialId);
        link.setMaterialType(materialType);
        link.setSortOrder(nextSortOrder(profile.getId()));
        link.setIsPrimary(isFirstOfType(profile.getId(), materialType) ? 1 : 0);
        link.setCreateTime(LocalDateTime.now());
        materialMapper.insert(link);

        log.info("[BodyProfile] 素材已关联 userId={} profileId={} materialId={} type={}",
                userId, profile.getId(), materialId, materialType);
        return toVO(profile, listMaterials(profile.getId(), userId));
    }

    @Override
    @Transactional
    public void unlinkMaterial(Long userId, Long materialId) {
        BodyProfile profile = selectByUserId(userId);
        if (profile == null) {
            return;
        }
        LambdaQueryWrapper<BodyProfileMaterial> wrapper = new LambdaQueryWrapper<BodyProfileMaterial>()
                .eq(BodyProfileMaterial::getProfileId, profile.getId())
                .eq(BodyProfileMaterial::getMaterialId, materialId);
        materialMapper.delete(wrapper);
        log.info("[BodyProfile] 素材已解除关联 userId={} profileId={} materialId={}",
                userId, profile.getId(), materialId);

        // 级联清理：该素材不再被任何档案引用时，删 OSS 文件 + 软删 material 记录
        cleanupOrphanMaterial(userId, materialId);
    }

    /**
     * 素材解除关联后，若已无档案引用，则通过 Feign 删除素材（软删 + 删 OSS 文件）
     */
    private void cleanupOrphanMaterial(Long userId, Long materialId) {
        Integer refCount = materialMapper.selectCount(
                new LambdaQueryWrapper<BodyProfileMaterial>()
                        .eq(BodyProfileMaterial::getMaterialId, materialId));
        if (refCount != null && refCount > 0) {
            // 仍被其他档案引用，不删除
            return;
        }
        try {
            materialClient.deleteMaterial(materialId, userId);
            log.info("[BodyProfile] 孤儿素材已清理 materialId={}", materialId);
        } catch (Exception e) {
            log.warn("[BodyProfile] 孤儿素材清理失败（交由定时任务兜底） materialId={}", materialId, e);
        }
    }

    @Override
    @Transactional
    public BodyProfileVO setPrimaryMaterial(Long userId, Long materialId) {
        BodyProfile profile = selectByUserId(userId);
        if (profile == null) {
            throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "档案不存在");
        }

        // 1. 找到目标素材关联，确定其类型
        BodyProfileMaterial target = materialMapper.selectOne(
                new LambdaQueryWrapper<BodyProfileMaterial>()
                        .eq(BodyProfileMaterial::getProfileId, profile.getId())
                        .eq(BodyProfileMaterial::getMaterialId, materialId));
        if (target == null) {
            throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "素材尚未关联到档案");
        }
        String materialType = target.getMaterialType();

        // 2. 同类型其他素材置为非主
        materialMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BodyProfileMaterial>()
                        .eq(BodyProfileMaterial::getProfileId, profile.getId())
                        .eq(BodyProfileMaterial::getMaterialType, materialType)
                        .set(BodyProfileMaterial::getIsPrimary, 0));

        // 3. 目标素材置为主
        materialMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BodyProfileMaterial>()
                        .eq(BodyProfileMaterial::getProfileId, profile.getId())
                        .eq(BodyProfileMaterial::getMaterialId, materialId)
                        .set(BodyProfileMaterial::getIsPrimary, 1));

        log.info("[BodyProfile] 已设置主素材 userId={} profileId={} materialId={} type={}",
                userId, profile.getId(), materialId, materialType);
        return toVO(profile, listMaterials(profile.getId(), userId));
    }

    // ==================== 私有方法 ====================

    /**
     * 获取档案，不存在则创建（懒创建）
     */
    private BodyProfile getOrCreate(Long userId) {
        BodyProfile profile = selectByUserId(userId);
        if (profile != null) {
            return profile;
        }
        profile = new BodyProfile();
        profile.setUserId(userId);
        profile.setName(DEFAULT_PROFILE_NAME);
        profile.setCreateTime(LocalDateTime.now());
        profile.setUpdateTime(LocalDateTime.now());
        save(profile);
        log.info("[BodyProfile] 懒创建档案 userId={} profileId={}", userId, profile.getId());
        return profile;
    }

    private BodyProfile selectByUserId(Long userId) {
        return lambdaQuery().eq(BodyProfile::getUserId, userId).one();
    }

    /**
     * 部分更新语义：非 null / 非空 字段才覆盖
     */
    private void applyBodyData(BodyProfile profile, UpsertProfileReq req) {
        if (StrUtil.isNotBlank(req.getName())) {
            profile.setName(req.getName());
        }
        if (req.getHeight() != null) profile.setHeight(req.getHeight());
        if (req.getWeight() != null) profile.setWeight(req.getWeight());
        if (req.getBust() != null) profile.setBust(req.getBust());
        if (req.getWaist() != null) profile.setWaist(req.getWaist());
        if (req.getHip() != null) profile.setHip(req.getHip());
        if (req.getShoulder() != null) profile.setShoulder(req.getShoulder());
        if (req.getArm() != null) profile.setArm(req.getArm());
        if (req.getLeg() != null) profile.setLeg(req.getLeg());
        if (StrUtil.isNotBlank(req.getBodyType())) {
            profile.setBodyType(BodyType.of(req.getBodyType()));
        }
        if (StrUtil.isNotBlank(req.getSkinTone())) {
            profile.setSkinTone(req.getSkinTone());
        }
        if (req.getModifyParts() != null) {
            profile.setModifyParts(JSONUtil.toJsonStr(req.getModifyParts()));
        }
        if (req.getRemark() != null) {
            profile.setRemark(req.getRemark());
        }
    }

    /**
     * 查询档案关联素材（按 sortOrder 排序），并批量填充临时访问URL
     */
    private List<ProfileMaterialVO> listMaterials(Long profileId, Long userId) {
        List<BodyProfileMaterial> links = materialMapper.selectList(
                new LambdaQueryWrapper<BodyProfileMaterial>()
                        .eq(BodyProfileMaterial::getProfileId, profileId)
                        .orderByAsc(BodyProfileMaterial::getSortOrder));
        if (links.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量获取临时访问URL
        List<Long> materialIds = links.stream()
                .map(BodyProfileMaterial::getMaterialId)
                .collect(Collectors.toList());
        Map<Long, String> accessUrlMap = Collections.emptyMap();
        try {
            BatchMaterialResult urls = materialClient.getMaterialAccessUrls(userId, materialIds);
            if (urls.getValidMaterials() != null) {
                accessUrlMap = urls.getValidMaterials().stream()
                        .collect(Collectors.toMap(MaterialDTO::getMaterialId, MaterialDTO::getAccessUrl, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("[BodyProfile] 批量获取素材访问链接失败 profileId={}", profileId, e);
        }

        final Map<Long, String> finalUrlMap = accessUrlMap;
        return links.stream().map(link -> {
            ProfileMaterialVO vo = new ProfileMaterialVO();
            vo.setMaterialId(link.getMaterialId());
            vo.setMaterialType(link.getMaterialType());
            vo.setIsPrimary(link.getIsPrimary());
            vo.setSortOrder(link.getSortOrder());
            vo.setAccessUrl(finalUrlMap.get(link.getMaterialId()));
            return vo;
        }).sorted(Comparator.comparing(ProfileMaterialVO::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())))
          .collect(Collectors.toList());
    }

    private Integer selectCount(Long profileId, Long materialId) {
        return materialMapper.selectCount(
                new LambdaQueryWrapper<BodyProfileMaterial>()
                        .eq(BodyProfileMaterial::getProfileId, profileId)
                        .eq(BodyProfileMaterial::getMaterialId, materialId));
    }

    private int nextSortOrder(Long profileId) {
        Integer count = materialMapper.selectCount(
                new LambdaQueryWrapper<BodyProfileMaterial>()
                        .eq(BodyProfileMaterial::getProfileId, profileId));
        return count == null ? 0 : count.intValue();
    }

    private boolean isFirstOfType(Long profileId, String materialType) {
        Integer count = materialMapper.selectCount(
                new LambdaQueryWrapper<BodyProfileMaterial>()
                        .eq(BodyProfileMaterial::getProfileId, profileId)
                        .eq(BodyProfileMaterial::getMaterialType, materialType));
        return count == null || count == 0;
    }

    /**
     * PO → VO（含关联素材）
     */
    private BodyProfileVO toVO(BodyProfile profile, List<ProfileMaterialVO> materials) {
        BodyProfileVO vo = new BodyProfileVO();
        vo.setProfileId(profile.getId());
        vo.setUserId(profile.getUserId());
        vo.setName(profile.getName());
        vo.setHeight(profile.getHeight());
        vo.setWeight(profile.getWeight());
        vo.setBust(profile.getBust());
        vo.setWaist(profile.getWaist());
        vo.setHip(profile.getHip());
        vo.setShoulder(profile.getShoulder());
        vo.setArm(profile.getArm());
        vo.setLeg(profile.getLeg());
        vo.setBodyType(profile.getBodyType() != null ? profile.getBodyType().getValue() : null);
        vo.setSkinTone(profile.getSkinTone());
        vo.setModifyParts(parseModifyParts(profile.getModifyParts()));
        vo.setRemark(profile.getRemark());
        vo.setMaterials(materials);
        vo.setCreateTime(profile.getCreateTime());
        vo.setUpdateTime(profile.getUpdateTime());
        return vo;
    }

    private List<String> parseModifyParts(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            log.warn("[BodyProfile] modifyParts 反序列化失败: {}", json, e);
            return Collections.emptyList();
        }
    }
}
