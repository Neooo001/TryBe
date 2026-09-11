package com.trybe.wardrobe.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trybe.common.domain.ErrorCodeEnum;
import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.common.exception.BadRequestException;
import com.trybe.common.exception.ForbiddenException;
import com.trybe.common.service.OssService;
import com.trybe.wardrobe.domain.dto.TaobaoImportReq;
import com.trybe.wardrobe.domain.dto.TaobaoProductDTO;
import com.trybe.wardrobe.domain.enums.WardrobeSource;
import com.trybe.wardrobe.domain.po.Product;
import com.trybe.wardrobe.domain.po.WardrobeItem;
import com.trybe.wardrobe.domain.vo.WardrobeItemVO;
import com.trybe.wardrobe.mapper.ProductMapper;
import com.trybe.wardrobe.mapper.WardrobeItemMapper;
import com.trybe.wardrobe.service.ITaobaoService;
import com.trybe.wardrobe.service.IWardrobeService;
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
import java.util.Collections;
import java.util.List;

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

    @Override
    @Transactional
    public WardrobeItemVO importFromTaobao(Long userId, TaobaoImportReq req) {
        // 1. 解析商品ID
        String numIidStr = TaobaoShareParser.parseNumIid(req.getShareText());
        Long numIid;
        try {
            numIid = Long.valueOf(numIidStr);
        } catch (NumberFormatException e) {
            throw new BadRequestException(ErrorCodeEnum.PARAM_FORMAT_ERR, "无法从分享链接中解析商品ID");
        }

        // 2. 商品去重：已存在则复用
        Product product = findByNumIid(numIid);
        if (product == null) {
            product = fetchAndSaveProduct(numIid);
        }

        // 3. 创建衣柜条目
        WardrobeItem item = new WardrobeItem();
        item.setUserId(userId);
        item.setSource(WardrobeSource.TAOBAO_IMPORT.getValue());
        item.setProductId(product.getId());
        item.setTitle(product.getTitle());
        item.setImageKey(product.getMainImageKey());
        item.setDetailImageKeys(product.getDetailImageKeys());
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        save(item);

        log.info("[Wardrobe] 淘宝导入成功 userId={} numIid={} wardrobeId={}", userId, numIid, item.getId());
        return toVO(item, product);
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

        WardrobeItem item = new WardrobeItem();
        item.setUserId(userId);
        item.setSource(WardrobeSource.MANUAL.getValue());
        item.setTitle(title);
        item.setImageKey(imageKey);
        item.setRemark(remark);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        save(item);

        log.info("[Wardrobe] 手动添加成功 userId={} wardrobeId={}", userId, item.getId());
        return toVO(item, null);
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

        WardrobeItem item = new WardrobeItem();
        item.setUserId(userId);
        item.setSource(WardrobeSource.CUSTOM_OUTFIT.getValue());
        item.setTitle(title);
        item.setImageKey(imageKey);
        item.setRemark(remark);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        save(item);

        log.info("[Wardrobe] 自定义穿搭添加成功 userId={} wardrobeId={}", userId, item.getId());
        return toVO(item, null);
    }

    @Override
    public PageDTO<WardrobeItemVO> listWardrobe(Long userId, String source, PageQuery pageQuery) {
        LambdaQueryWrapper<WardrobeItem> wrapper = new LambdaQueryWrapper<WardrobeItem>()
                .eq(WardrobeItem::getUserId, userId);

        if (StrUtil.isNotBlank(source)) {
            wrapper.eq(WardrobeItem::getSource, WardrobeSource.of(source).getValue());
        }

        Page<WardrobeItem> page = pageQuery.toMpPageDefaultSortByCreateTimeDesc();
        page = baseMapper.selectPage(page, wrapper);

        return PageDTO.of(page, item -> toVO(item, findProductById(item.getProductId())));
    }

    @Override
    public WardrobeItemVO getDetail(Long userId, Long wardrobeId) {
        WardrobeItem item = getOwnedItem(userId, wardrobeId);
        return toVO(item, findProductById(item.getProductId()));
    }

    @Override
    @Transactional
    public WardrobeItemVO updateItem(Long userId, Long wardrobeId, String title, String remark) {
        WardrobeItem item = getOwnedItem(userId, wardrobeId);
        if (StrUtil.isNotBlank(title)) {
            item.setTitle(title);
        }
        if (remark != null) {
            item.setRemark(remark);
        }
        item.setUpdateTime(LocalDateTime.now());
        updateById(item);
        log.info("[Wardrobe] 编辑成功 userId={} wardrobeId={}", userId, wardrobeId);
        return toVO(item, findProductById(item.getProductId()));
    }

    @Override
    @Transactional
    public void deleteItem(Long userId, Long wardrobeId) {
        WardrobeItem item = getOwnedItem(userId, wardrobeId);

        // 手动/穿搭来源：删除用户目录下的图片文件；淘宝导入仅解除关联，不动公共商品与图片
        if (WardrobeSource.MANUAL.getValue().equals(item.getSource())
                || WardrobeSource.CUSTOM_OUTFIT.getValue().equals(item.getSource())) {
            deleteFileQuietly(item.getImageKey());
            deleteFileQuietly(parseKeys(item.getDetailImageKeys()));
        }

        removeById(wardrobeId);
        log.info("[Wardrobe] 删除成功 userId={} wardrobeId={} source={}", userId, wardrobeId, item.getSource());
    }

    // ==================== 私有方法 ====================

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
        product.setSmallImages(JSONUtil.toJsonStr(dto.getSmallImages() == null ? Collections.emptyList() : dto.getSmallImages()));
        product.setReservePrice(dto.getReservePrice());
        product.setZkFinalPrice(dto.getZkFinalPrice());
        product.setItemUrl(dto.getItemUrl());
        product.setSellerId(dto.getSellerId());
        product.setShopTitle(dto.getShopTitle());
        product.setVolume(dto.getVolume());
        product.setProvcity(dto.getProvcity());
        product.setCategory(dto.getCategory());
        product.setRawJson(dto.getRawJson());

        // 下载主图 + 详情图并上传 OSS
        String mainKey = downloadAndUpload(dto.getPictUrl(), WardrobeOssPathBuilder.productMain(numIid));
        List<String> detailKeys = new ArrayList<>();
        if (dto.getSmallImages() != null) {
            int index = 1;
            for (String url : dto.getSmallImages()) {
                String key = downloadAndUpload(url, WardrobeOssPathBuilder.productDetail(numIid, index));
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

    private String downloadAndUpload(String url, String objectKey) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        try {
            byte[] bytes = HttpUtil.downloadBytes(url);
            try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
                ossService.putObject(in, objectKey);
            }
            return objectKey;
        } catch (Exception e) {
            log.warn("[Wardrobe] 图片下载或上传失败 url={} objectKey={}", url, objectKey, e);
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

    private WardrobeItemVO toVO(WardrobeItem item, Product product) {
        WardrobeItemVO vo = new WardrobeItemVO();
        vo.setWardrobeId(item.getId());
        vo.setSource(item.getSource());
        vo.setProductId(item.getProductId());
        vo.setTitle(item.getTitle());
        vo.setRemark(item.getRemark());
        vo.setCreateTime(item.getCreateTime());

        vo.setImageUrl(presign(item.getImageKey()));
        vo.setDetailImageUrls(presignList(parseKeys(item.getDetailImageKeys())));

        if (product != null) {
            vo.setNumIid(product.getNumIid());
            vo.setReservePrice(product.getReservePrice());
            vo.setZkFinalPrice(product.getZkFinalPrice());
            vo.setItemUrl(product.getItemUrl());
            vo.setShopTitle(product.getShopTitle());
        }
        return vo;
    }

    private String presign(String objectKey) {
        return StrUtil.isBlank(objectKey) ? null : ossService.generatePresignedUrl(objectKey);
    }

    private List<String> presignList(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>();
        for (String key : objectKeys) {
            String url = presign(key);
            if (url != null) {
                urls.add(url);
            }
        }
        return urls;
    }

    private List<String> parseKeys(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            log.warn("[Wardrobe] objectKey列表反序列化失败: {}", json, e);
            return Collections.emptyList();
        }
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
        if (objectKeys == null) {
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
