package com.trybe.material.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trybe.api.dto.BatchMaterialResult;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.material.domain.dto.PreSignUploadReq;
import com.trybe.material.domain.dto.UploadCallbackReq;
import com.trybe.material.domain.po.Material;
import com.trybe.material.domain.vo.MaterialVO;
import com.trybe.material.domain.vo.PreSignUploadVO;

import java.util.List;

public interface IMaterialService extends IService<Material> {

    /**
     * 生成预签名上传URL
     */
    PreSignUploadVO generatePreSignUploadUrl(Long userId, PreSignUploadReq req);

    /**
     * 上传成功回调，触发异步校验
     */
    void uploadCallback(Long userId, UploadCallbackReq req);

    /**
     * 查询素材详情（含临时访问URL）
     */
    MaterialVO getMaterialDetail(Long userId, Long materialId);

    /**
     * 分页查询用户素材列表
     *
     * @param userId       用户ID
     * @param materialType 素材类型（可选）
     * @param pageQuery    分页参数
     * @return 分页结果
     */
    PageDTO<MaterialVO> listUserMaterials(Long userId, String materialType, PageQuery pageQuery);

    /**
     * 删除素材
     */
    void deleteMaterial(Long userId, Long materialId);

    /**
     * 标记素材校验失败（保留 FAILED 状态便于前端查询，删除临时文件）
     *
     * @param materialId 素材ID
     * @param failReason 失败原因
     */
    void markFailed(Long materialId, String failReason);

    /**
     * 归档校验失败超时的素材（FAILED 超时 → 软删除）
     *
     * @param beforeTime 失败早于该时间的素材
     * @return 清理数量
     */
    int archiveFailed(java.time.LocalDateTime beforeTime);

    /**
     * 清理上传僵尸素材（PENDING_UPLOAD 超时未回调 → 软删除 + 删除临时文件）
     *
     * @param beforeTime 早于该时间的素材视为僵尸
     * @return 清理数量
     */
    int cleanZombie(java.time.LocalDateTime beforeTime);

    /**
     * 物理清理已归档（软删除）素材及其校验日志、OSS 文件
     *
     * @param beforeTime 软删除早于该时间的素材
     * @return 清理数量
     */
    int physicallyClean(java.time.LocalDateTime beforeTime);

    /**
     * 批量校验素材有效性 — Feign 调用
     *
     * @param userId      用户ID
     * @param materialIds 素材ID列表
     * @return 有效素材 + 无效ID
     */
    BatchMaterialResult validateMaterials(Long userId, List<Long> materialIds);

    /**
     * 批量获取素材临时访问链接 — Feign 调用
     *
     * @param userId      用户ID
     * @param materialIds 素材ID列表
     * @return 有效素材(含临时URL) + 无效ID
     */
    BatchMaterialResult getMaterialAccessUrls(Long userId, List<Long> materialIds);
}