package com.trybe.wardrobe.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.BizIllegalException;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.service.OssService;
import com.trybe.wardrobe.domain.dto.UpdateWardrobeReq;
import com.trybe.wardrobe.domain.enums.GarmentCategoryL1;
import com.trybe.wardrobe.domain.enums.WardrobeSource;
import com.trybe.wardrobe.domain.enums.WearStatus;
import com.trybe.wardrobe.domain.po.Product;
import com.trybe.wardrobe.domain.po.WardrobeItem;
import com.trybe.wardrobe.domain.vo.WardrobeItemVO;
import com.trybe.wardrobe.mapper.ProductMapper;
import com.trybe.wardrobe.mapper.WardrobeItemMapper;
import com.trybe.wardrobe.service.IWardrobeService;
import com.trybe.wardrobe.service.WardrobeItemConverter;
import com.trybe.wardrobe.util.WardrobeOssPathBuilder;
import com.trybe.wardrobe.util.WardrobeSortParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 衣柜服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WardrobeServiceImpl extends ServiceImpl<WardrobeItemMapper, WardrobeItem> implements IWardrobeService {

    private final ProductMapper productMapper;
    private final OssService ossService;
    private final WardrobeItemConverter converter;

    /**
     * 手动添加
     *
     * <p>⚠️ 不加事务：图片要先传到 OSS（外网 IO），再把 key 落库。放进事务里等于抱着数据库连接等上传，
     * 大图/弱网时一条连接会被占用十几秒，是连接池被打满的另一条路径。只有一次 insert，无需事务。
     */
    @Override
    public WardrobeItemVO addManual(Long userId, String title, String remark, MultipartFile image) {
        if (StrUtil.isBlank(title)) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "标题不能为空");
        }
        if (image == null || image.isEmpty()) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "衣服图片不能为空");
        }
        String imageKey = WardrobeOssPathBuilder.custom(userId, extensionOf(image.getOriginalFilename()));
        uploadMultipart(image, imageKey);

        WardrobeItem item = newItem(userId, WardrobeSource.MANUAL);
        item.setTitle(title);
        item.setImageKey(imageKey);
        item.setOriginImageKey(imageKey);
        item.setRemark(remark);
        save(item);

        log.info("[Wardrobe] 手动添加成功 userId={} wardrobeId={}", userId, item.getId());
        return converter.toVO(item, null);
    }

    /**
     * 自定义穿搭上传
     *
     * <p>⚠️ 不加事务：同 {@link #addManual}，先传 OSS 再插一条记录。
     */
    @Override
    public WardrobeItemVO addOutfit(Long userId, String title, String remark, MultipartFile image) {
        if (StrUtil.isBlank(title)) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "标题不能为空");
        }
        if (image == null || image.isEmpty()) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "穿搭照片不能为空");
        }
        String imageKey = WardrobeOssPathBuilder.outfit(userId, extensionOf(image.getOriginalFilename()));
        uploadMultipart(image, imageKey);

        WardrobeItem item = newItem(userId, WardrobeSource.CUSTOM_OUTFIT);
        item.setTitle(title);
        item.setImageKey(imageKey);
        item.setOriginImageKey(imageKey);
        item.setRemark(remark);
        save(item);

        log.info("[Wardrobe] 自定义穿搭添加成功 userId={} wardrobeId={}", userId, item.getId());
        return converter.toVO(item, null);
    }

    @Override
    public PageDTO<WardrobeItemVO> listWardrobe(Long userId, String source, String categoryL1,
                                                String wearStatus, String keyword, String sorts, PageQuery pageQuery) {
        LambdaQueryWrapper<WardrobeItem> wrapper = new LambdaQueryWrapper<WardrobeItem>()
                .eq(WardrobeItem::getUserId, userId);

        if (StrUtil.isNotBlank(source)) {
            wrapper.eq(WardrobeItem::getSource, WardrobeSource.of(source).getValue());
        }
        if (StrUtil.isNotBlank(categoryL1)) {
            wrapper.eq(WardrobeItem::getCategoryL1, categoryL1.trim().toUpperCase());
        }
        if (StrUtil.isNotBlank(wearStatus)) {
            wrapper.eq(WardrobeItem::getWearStatus, WearStatus.of(wearStatus).getValue());
        }
        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(WardrobeItem::getTitle, kw)
                    .or().like(WardrobeItem::getBrand, kw)
                    .or().like(WardrobeItem::getRemark, kw));
        }

        // 排序走衣柜自有的白名单解析器（含空值沉底 + id 稳定 tie-breaker），
        // 有意不再使用 PageQuery.toMpPageDefaultSortByCreateTimeDesc()，以关闭「sortBy 直接当列名」的注入面。
        // pageNo/pageSize 取自 PageQuery（带默认值），此处再判空兜底，避免手工构造 PageQuery 时 NPE。
        Integer pageNo = pageQuery == null || pageQuery.getPageNo() == null
                ? PageQuery.DEFAULT_PAGE_NUM : pageQuery.getPageNo();
        Integer pageSize = pageQuery == null || pageQuery.getPageSize() == null
                ? PageQuery.DEFAULT_PAGE_SIZE : pageQuery.getPageSize();
        Page<WardrobeItem> page = new Page<>(pageNo, pageSize);
        page.addOrder(WardrobeSortParser.parse(sorts, pageQuery));
        page = baseMapper.selectPage(page, wrapper);

        return PageDTO.of(page, item -> converter.toVO(item, findProductById(item.getProductId())));
    }

    @Override
    public WardrobeItemVO getDetail(Long userId, Long wardrobeId) {
        WardrobeItem item = getOwnedItem(userId, wardrobeId);
        return converter.toVO(item, findProductById(item.getProductId()));
    }

    /**
     * 按一级分类统计数量：一条 GROUP BY 拿全量，避免前端按分类逐个请求。
     *
     * <p>未归类（category_l1 为 null/空/非法值）统一归口到 {@link GarmentCategoryL1#OTHER}，
     * 与前端「未分类」Tab 的 key 对齐（否则这类条目只进得去「全部」、在任何 Tab 上都看不到）。
     */
    @Override
    public Map<String, Integer> countByCategory(Long userId) {
        List<Map<String, Object>> rows = baseMapper.selectMaps(new QueryWrapper<WardrobeItem>()
                .select("category_l1", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .groupBy("category_l1"));

        Map<String, Integer> result = new HashMap<>();
        int total = 0;
        for (Map<String, Object> row : rows) {
            Object code = pick(row, "category_l1");
            Object cnt = pick(row, "cnt");
            // 复用宽松解析器归一：null/空/大写小写/中文名都能落到正确的 code，兜底为 OTHER
            String key = GarmentCategoryL1.of(code == null ? null : String.valueOf(code)).getCode();
            int count = cnt == null ? 0 : ((Number) cnt).intValue();
            // 同一 key 理论上不会重复，这里做一次累加，防御 SQL 层出现多行同码
            result.merge(key, count, Integer::sum);
            total += count;
        }
        result.put("ALL", total);
        return result;
    }

    /**
     * 容错取列：不同 JDBC/MP 版本下 selectMaps 返回 map 的 key 可能是别名、下划线原名或大小写变体，
     * 直接 get 取不到会静默归零，这里做归一化匹配兜底。
     */
    private Object pick(Map<String, Object> row, String column) {
        if (row.containsKey(column)) {
            return row.get(column);
        }
        String normalized = column.replace("_", "").toLowerCase();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().replace("_", "").toLowerCase();
            if (key.equals(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    @Transactional
    public WardrobeItemVO updateItem(Long userId, Long wardrobeId, UpdateWardrobeReq req) {
        WardrobeItem item = getOwnedItem(userId, wardrobeId);
        converter.applyUpdate(item, req);
        updateById(item);
        log.info("[Wardrobe] 编辑成功 userId={} wardrobeId={}", userId, wardrobeId);
        return converter.toVO(item, findProductById(item.getProductId()));
    }

    /**
     * 删除衣柜条目
     *
     * <p>⚠️ 不加事务，且顺序固定为「先删库、再清 OSS」：
     * 删库是单条操作本身原子；OSS 删除是最多 4~5 次外网调用，
     * 放进事务里会持着数据库连接等网络（同样是连接池耗尽的来源）。
     * 若 OSS 清理失败，库里已经没有这条记录，只会留下无人引用的对象，不影响用户。
     */
    @Override
    public void deleteItem(Long userId, Long wardrobeId) {
        WardrobeItem item = getOwnedItem(userId, wardrobeId);

        removeById(wardrobeId);

        // 用户私有来源（AI导入/手动/穿搭）：删除用户目录下的图片文件
        // 历史遗留：来源为 TAOBAO_IMPORT 的老数据只删条目、不动公共商品图片
        if (!WardrobeSource.TAOBAO_IMPORT.getValue().equals(item.getSource())) {
            deleteFileQuietly(item.getImageKey());
            deleteFileQuietly(item.getOriginImageKey());
            deleteFileQuietly(item.getCutoutImageKey());
            deleteFileQuietly(converter.toStringList(item.getDetailImageKeys()));
        }
        log.info("[Wardrobe] 删除成功 userId={} wardrobeId={} source={}", userId, wardrobeId, item.getSource());
    }

    // ==================== 私有方法 ====================

    /**
     * 构造带默认值的衣柜条目（统一初始化穿着状态与穿着次数）
     */
    private WardrobeItem newItem(Long userId, WardrobeSource source) {
        WardrobeItem item = new WardrobeItem();
        item.setUserId(userId);
        item.setSource(source.getValue());
        item.setWearStatus(WearStatus.WEARING.getValue());
        item.setWearCount(0);
        item.setAiRecognized(0);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        return item;
    }

    /**
     * 解析商品ID：快速路径 → API兜底
     */
 

    /**
     * 查询并落库商品（含下载图片上传 OSS）
     */
 

    private String uploadBytes(byte[] bytes, String objectKey) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            ossService.putObject(in, objectKey);
            return objectKey;
        } catch (IOException e) {
            log.warn("[Wardrobe] 图片上传OSS失败 objectKey={}", objectKey, e);
            return null;
        }
    }

    private void uploadMultipart(MultipartFile file, String objectKey) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(file.getBytes())) {
            ossService.putObject(in, objectKey);
        } catch (IOException e) {
            log.error("[Wardrobe] 图片上传失败 objectKey={}", objectKey, e);
            throw new BadRequestException(ErrorCodeEnum.THIRD_PARTY_ERROR, "图片上传失败，请稍后再试");
        }
    }

 

    private Product findProductById(Long productId) {
        if (productId == null) {
            return null;
        }
        return productMapper.selectById(productId);
    }

    private WardrobeItem getOwnedItem(Long userId, Long wardrobeId) {
        WardrobeItem item = getById(wardrobeId);
        if (item == null) {
            throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "衣柜条目不存在");
        }
        if (!item.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCodeEnum.FORBIDDEN, "无权操作该条目");
        }
        return item;
    }

    private void deleteFileQuietly(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            return;
        }
        try {
            ossService.deleteFile(objectKey);
        } catch (Exception e) {
            log.warn("[Wardrobe] OSS文件删除失败 objectKey={}", objectKey, e);
        }
    }

    private void deleteFileQuietly(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        for (String key : objectKeys) {
            deleteFileQuietly(key);
        }
    }

    private String extensionOf(String filename) {
        return StrUtil.subAfter(filename, ".", true);
    }
}
