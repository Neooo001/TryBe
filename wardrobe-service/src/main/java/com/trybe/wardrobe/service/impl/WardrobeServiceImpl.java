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
import com.trybe.wardrobe.config.TaobaoProperties;
import com.trybe.wardrobe.domain.dto.TaobaoImportReq;
import com.trybe.wardrobe.domain.dto.TaobaoProductDTO;
import com.trybe.wardrobe.domain.dto.UpdateWardrobeReq;
import com.trybe.wardrobe.domain.enums.GarmentCategoryL1;
import com.trybe.wardrobe.domain.enums.WardrobeSource;
import com.trybe.wardrobe.domain.enums.WearStatus;
import com.trybe.wardrobe.domain.po.Product;
import com.trybe.wardrobe.domain.po.WardrobeItem;
import com.trybe.wardrobe.domain.vo.WardrobeItemVO;
import com.trybe.wardrobe.mapper.ProductMapper;
import com.trybe.wardrobe.mapper.WardrobeItemMapper;
import com.trybe.wardrobe.service.ITaobaoService;
import com.trybe.wardrobe.service.IWardrobeService;
import com.trybe.wardrobe.service.WardrobeItemConverter;
import com.trybe.wardrobe.util.TaobaoImageUtil;
import com.trybe.wardrobe.util.TaobaoShareParser;
import com.trybe.wardrobe.util.WardrobeOssPathBuilder;
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
    private final ITaobaoService taobaoService;
    private final OssService ossService;
    private final TaobaoProperties taobaoProperties;
    private final WardrobeItemConverter converter;

    /**
     * 从淘宝导入
     */
    @Override
    @Transactional
    public WardrobeItemVO importFromTaobao(Long userId, TaobaoImportReq req) {
        // 1. 解析商品ID：快速路径（本地正则+重定向）→ API兜底（taobao.tbk.tpwd.parse）
        Long numIid = resolveNumIid(req.getShareText());
        log.info("[Wardrobe] 解析商品ID shareText={} numIid={}", req.getShareText(), numIid);

        // 2. 商品去重：已存在则复用
        Product product = findByNumIid(numIid);
        if (product == null) {
            product = fetchAndSaveProduct(numIid);
        }

        // 3. 创建衣柜条目（衣服属性留空，用户可在详情页补充；后续由 AI 补全任务填充）
        WardrobeItem item = newItem(userId, WardrobeSource.TAOBAO_IMPORT);
        item.setProductId(product.getId());
        item.setTitle(product.getTitle());
        item.setImageKey(product.getMainImageKey());
        item.setDetailImageKeys(product.getDetailImageKeys());
        save(item);

        log.info("[Wardrobe] 淘宝导入成功 userId={} numIid={} wardrobeId={}", userId, numIid, item.getId());
        return converter.toVO(item, product);
    }

    @Override
    @Transactional
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

    @Override
    @Transactional
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
                                                String wearStatus, String keyword, PageQuery pageQuery) {
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

        Page<WardrobeItem> page = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
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

    @Override
    @Transactional
    public void deleteItem(Long userId, Long wardrobeId) {
        WardrobeItem item = getOwnedItem(userId, wardrobeId);

        // 用户私有来源（AI导入/手动/穿搭）：删除用户目录下的图片文件
        // 淘宝导入仅解除关联，不动公共商品与图片
        if (!WardrobeSource.TAOBAO_IMPORT.getValue().equals(item.getSource())) {
            deleteFileQuietly(item.getImageKey());
            deleteFileQuietly(item.getOriginImageKey());
            deleteFileQuietly(item.getCutoutImageKey());
            deleteFileQuietly(converter.toStringList(item.getDetailImageKeys()));
        }

        removeById(wardrobeId);
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
    private Long resolveNumIid(String shareText) {
        if (StrUtil.isBlank(shareText)) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_EMPTY, "分享文案不能为空");
        }

        String numIidStr = TaobaoShareParser.parseNumIid(shareText);
        if (numIidStr != null) {
            try {
                return Long.valueOf(numIidStr);
            } catch (NumberFormatException e) {
                log.warn("[Wardrobe] 本地解析商品ID格式错误 numIidStr={}", numIidStr);
            }
        }

        Long numIid = taobaoService.tpwdParse(shareText);
        if (numIid != null) {
            return numIid;
        }

        throw new BadRequestException(ErrorCodeEnum.PARAM_FORMAT_ERR, "无法从分享链接中解析商品ID");
    }

    /**
     * 查询并落库商品（含下载图片上传 OSS）
     */
    private Product fetchAndSaveProduct(Long numIid) {
        TaobaoProductDTO dto = taobaoService.getItemInfo(numIid);
        if (dto == null) {
            throw new BadRequestException(ErrorCodeEnum.DATA_NOT_FOUND, "未查询到该商品信息");
        }

        Product product = new Product();
        product.setNumIid(numIid);
        product.setTitle(dto.getTitle());
        product.setPictUrl(dto.getPictUrl());
        product.setSmallImages(JSONUtil.toJsonStr(dto.getSmallImages() == null ? new ArrayList<String>() : dto.getSmallImages()));
        product.setReservePrice(dto.getReservePrice());
        product.setZkFinalPrice(dto.getZkFinalPrice());
        product.setItemUrl(dto.getItemUrl());
        product.setSellerId(dto.getSellerId());
        product.setShopTitle(dto.getShopTitle());
        product.setVolume(dto.getVolume());
        product.setProvcity(dto.getProvcity());
        product.setCategory(dto.getCategory());
        product.setRawJson(dto.getRawJson());

        // 主图是试穿的输入素材，必须成功；详情图失败可跳过，不影响主流程
        byte[] mainBytes = TaobaoImageUtil.download(dto.getPictUrl());
        if (mainBytes == null) {
            log.warn("[Wardrobe] 商品主图下载失败 numIid={} pictUrl={}", numIid, dto.getPictUrl());
            throw new BizIllegalException(ErrorCodeEnum.THIRD_PARTY_ERROR, "商品主图获取失败，请稍后重试");
        }
        String mainKey = uploadBytes(mainBytes, WardrobeOssPathBuilder.productMain(numIid));

        List<String> detailKeys = new ArrayList<>();
        if (dto.getSmallImages() != null) {
            int max = Math.max(0, taobaoProperties.getMaxDetailImages());
            int index = 1;
            for (String url : dto.getSmallImages()) {
                if (detailKeys.size() >= max) {
                    break;
                }
                byte[] bytes = TaobaoImageUtil.download(url);
                if (bytes == null) {
                    continue;
                }
                String key = uploadBytes(bytes, WardrobeOssPathBuilder.productDetail(numIid, index));
                if (key != null) {
                    detailKeys.add(key);
                }
                index++;
            }
        }

        product.setMainImageKey(mainKey);
        product.setDetailImageKeys(JSONUtil.toJsonStr(detailKeys));
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.insert(product);

        log.info("[Wardrobe] 商品落库成功 numIid={} productId={} detailCount={}", numIid, product.getId(), detailKeys.size());
        return product;
    }

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

    private Product findByNumIid(Long numIid) {
        return productMapper.selectOne(new LambdaQueryWrapper<Product>().eq(Product::getNumIid, numIid));
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
