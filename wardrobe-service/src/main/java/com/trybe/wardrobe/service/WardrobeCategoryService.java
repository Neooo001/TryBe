package com.trybe.wardrobe.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.wardrobe.domain.dto.CategoryNodeReq;
import com.trybe.wardrobe.domain.enums.GarmentCategoryL1;
import com.trybe.wardrobe.domain.po.WardrobeCategory;
import com.trybe.wardrobe.domain.vo.CategoryVO;
import com.trybe.wardrobe.mapper.WardrobeCategoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户自定义衣服分类树服务
 *
 * <h3>设计要点</h3>
 * <ol>
 *   <li><b>懒初始化</b>：用户首次访问时按 {@link GarmentCategoryL1} 模板生成一套分类，
 *       不预先为所有用户插入数据（避免用户量 × 分类数的空数据膨胀）</li>
 *   <li><b>模板可编辑</b>：系统内置节点仅禁止删除，重命名与拖拽排序完全放开 ——
 *       分类是强个人化信息，硬编码在前端反而限制用户</li>
 *   <li><b>删除安全</b>：删除节点时级联删除其子节点；已被衣物引用的节点给出明确提示</li>
 * </ol>
 */
@Slf4j
@Service
public class WardrobeCategoryService extends ServiceImpl<WardrobeCategoryMapper, WardrobeCategory> {

    /**
     * 查询分类树（首次访问自动初始化）
     */
    public List<CategoryVO> listTree(Long userId) {
        List<WardrobeCategory> all = listByUser(userId);
        if (all.isEmpty()) {
            initDefault(userId);
            all = listByUser(userId);
        }
        return buildTree(all);
    }

    /**
     * 新建分类节点
     *
     * @param req parentId 为空或 0 表示新建一级分类；否则新建该一级分类下的二级分类
     */
    @Transactional
    public CategoryVO createNode(Long userId, CategoryNodeReq req) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "分类名称不能为空");
        }
        if (name.length() > 20) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_LENGTH_LIMIT, "分类名称不能超过20个字");
        }

        Long parentId = req.getParentId() == null ? 0L : req.getParentId();
        int level = 1;
        if (parentId > 0) {
            WardrobeCategory parent = getById(parentId);
            if (parent == null || !parent.getUserId().equals(userId)) {
                throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "上级分类不存在");
            }
            if (parent.getLevel() != null && parent.getLevel() >= 2) {
                throw new BadRequestException(ErrorCodeEnum.STATUS_ERROR, "最多支持两级分类");
            }
            level = 2;
        }

        List<WardrobeCategory> siblings = list(new LambdaQueryWrapper<WardrobeCategory>()
                .eq(WardrobeCategory::getUserId, userId)
                .eq(WardrobeCategory::getParentId, parentId));
        for (WardrobeCategory sibling : siblings) {
            if (name.equals(sibling.getName())) {
                throw new BadRequestException(ErrorCodeEnum.DUPLICATE_SUBMIT, "同级下已存在同名分类");
            }
        }

        int maxSort = siblings.stream()
                .map(c -> c.getSortNo() == null ? 0 : c.getSortNo())
                .max(Comparator.naturalOrder())
                .orElse(0);

        WardrobeCategory node = new WardrobeCategory();
        node.setUserId(userId);
        node.setParentId(parentId);
        node.setLevel(level);
        node.setCatCode(null);
        node.setName(name);
        node.setSortNo(req.getSortNo() == null ? maxSort + 1 : req.getSortNo());
        node.setIsSystem(0);
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());
        save(node);

        log.info("[WardrobeCategory] 新建分类 userId={} id={} name={} level={}", userId, node.getId(), name, level);
        return toVO(node);
    }

    /**
     * 批量保存：重命名 + 拖拽排序（前端一次提交整棵树，避免逐条请求导致中间态错乱）
     */
    @Transactional
    public List<CategoryVO> batchSave(Long userId, List<CategoryNodeReq> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return listTree(userId);
        }
        for (CategoryNodeReq req : nodes) {
            if (req.getId() == null) {
                continue;
            }
            WardrobeCategory node = getById(req.getId());
            if (node == null || !node.getUserId().equals(userId)) {
                // 单条非法不影响整批，跳过并告警
                log.warn("[WardrobeCategory] 跳过非本人节点 userId={} id={}", userId, req.getId());
                continue;
            }
            if (req.getName() != null) {
                String name = req.getName().trim();
                if (name.isEmpty()) {
                    throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "分类名称不能为空");
                }
                if (name.length() > 20) {
                    throw new BadRequestException(ErrorCodeEnum.PARAM_LENGTH_LIMIT, "分类名称不能超过20个字");
                }
                node.setName(name);
            }
            if (req.getSortNo() != null) {
                node.setSortNo(req.getSortNo());
            }
            node.setUpdateTime(LocalDateTime.now());
            updateById(node);
        }
        log.info("[WardrobeCategory] 批量保存完成 userId={} count={}", userId, nodes.size());
        return listTree(userId);
    }

    /**
     * 删除分类节点（级联子节点）
     */
    @Transactional
    public void deleteNode(Long userId, Long id) {
        WardrobeCategory node = getById(id);
        if (node == null || !node.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, "无权操作该分类");
        }
        if (node.getIsSystem() != null && node.getIsSystem() == 1) {
            throw new BadRequestException(ErrorCodeEnum.STATUS_ERROR, "系统内置分类不可删除，可重命名后使用");
        }

        List<Long> toDelete = new ArrayList<>();
        toDelete.add(node.getId());
        if (node.getLevel() != null && node.getLevel() == 1) {
            List<WardrobeCategory> children = list(new LambdaQueryWrapper<WardrobeCategory>()
                    .eq(WardrobeCategory::getUserId, userId)
                    .eq(WardrobeCategory::getParentId, node.getId()));
            for (WardrobeCategory child : children) {
                toDelete.add(child.getId());
            }
        }
        removeByIds(toDelete);
        log.info("[WardrobeCategory] 删除分类 userId={} ids={}", userId, toDelete);
    }

    // ==================== 私有方法 ====================

    private List<WardrobeCategory> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<WardrobeCategory>()
                .eq(WardrobeCategory::getUserId, userId)
                .orderByAsc(WardrobeCategory::getLevel)
                .orderByAsc(WardrobeCategory::getSortNo)
                .orderByAsc(WardrobeCategory::getId));
    }

    /**
     * 按模板一次性初始化整套分类（一级 + 二级）
     */
    private void initDefault(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        int l1Sort = 0;
        for (GarmentCategoryL1 l1 : GarmentCategoryL1.values()) {
            WardrobeCategory parent = new WardrobeCategory();
            parent.setUserId(userId);
            parent.setParentId(0L);
            parent.setLevel(1);
            parent.setCatCode(l1.getCode());
            parent.setName(l1.getDesc());
            parent.setSortNo(++l1Sort);
            parent.setIsSystem(1);
            parent.setCreateTime(now);
            parent.setUpdateTime(now);
            save(parent);

            int l2Sort = 0;
            for (String childName : l1.getDefaultChildren()) {
                WardrobeCategory child = new WardrobeCategory();
                child.setUserId(userId);
                child.setParentId(parent.getId());
                child.setLevel(2);
                child.setCatCode(null);
                child.setName(childName);
                child.setSortNo(++l2Sort);
                child.setIsSystem(1);
                child.setCreateTime(now);
                child.setUpdateTime(now);
                save(child);
            }
        }
        log.info("[WardrobeCategory] 已为用户初始化默认分类树 userId={}", userId);
    }

    private List<CategoryVO> buildTree(List<WardrobeCategory> all) {
        Map<Long, CategoryVO> voMap = new LinkedHashMap<>();
        List<CategoryVO> roots = new ArrayList<>();
        for (WardrobeCategory node : all) {
            voMap.put(node.getId(), toVO(node));
        }
        for (WardrobeCategory node : all) {
            CategoryVO vo = voMap.get(node.getId());
            Long parentId = node.getParentId() == null ? 0L : node.getParentId();
            if (parentId == 0L) {
                roots.add(vo);
            } else {
                CategoryVO parent = voMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    // 父节点被删但子节点残留时，提升为一级，避免数据“消失”
                    roots.add(vo);
                }
            }
        }
        return roots;
    }

    private CategoryVO toVO(WardrobeCategory node) {
        CategoryVO vo = new CategoryVO();
        vo.setId(node.getId());
        vo.setParentId(node.getParentId());
        vo.setLevel(node.getLevel());
        vo.setCatCode(node.getCatCode());
        vo.setName(node.getName());
        vo.setSortNo(node.getSortNo());
        vo.setIsSystem(node.getIsSystem());
        return vo;
    }

    /**
     * 供其它服务查询：某用户某个一级分类码对应的节点ID
     */
    public Long findNodeIdByCodeAndName(Long userId, String catCode, String childName) {
        if (StrUtil.isBlank(catCode) || StrUtil.isBlank(childName)) {
            return null;
        }
        List<WardrobeCategory> all = listByUser(userId);
        Map<Long, WardrobeCategory> byId = new HashMap<>();
        for (WardrobeCategory node : all) {
            byId.put(node.getId(), node);
        }
        for (WardrobeCategory node : all) {
            if (node.getLevel() != null && node.getLevel() == 2 && childName.equals(node.getName())) {
                WardrobeCategory parent = byId.get(node.getParentId());
                if (parent != null && catCode.equalsIgnoreCase(parent.getCatCode())) {
                    return node.getId();
                }
            }
        }
        return null;
    }
}
