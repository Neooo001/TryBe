package com.trybe.wardrobe.service;

import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.wardrobe.domain.dto.UpdateWardrobeReq;
import com.trybe.wardrobe.domain.vo.WardrobeItemVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 衣柜服务
 */
public interface IWardrobeService {

    /**
     * 手动添加
     */
    WardrobeItemVO addManual(Long userId, String title, String remark, MultipartFile image);

    /**
     * 自定义穿搭上传
     */
    WardrobeItemVO addOutfit(Long userId, String title, String remark, MultipartFile image);

    /**
     * 分页查询衣柜
     *
     * @param source     来源筛选，可空
     * @param categoryL1 一级分类筛选，可空
     * @param wearStatus 穿着状态筛选，可空
     * @param keyword    标题/品牌/备注模糊匹配，可空
     * @param sorts      多字段排序，格式 {@code field:asc|desc} 逗号分隔（如 {@code price:desc,wear_count:desc}）。
     *                   字段白名单：create_time/update_time/price/wear_count/cost_per_wear/purchase_date；
     *                   非法字段自动忽略并回落默认 create_time DESC。为空时回落 pageQuery 的 sortBy/isAsc。
     */
    PageDTO<WardrobeItemVO> listWardrobe(Long userId, String source, String categoryL1,
                                         String wearStatus, String keyword, String sorts, PageQuery pageQuery);

    /**
     * 衣柜详情
     */
    WardrobeItemVO getDetail(Long userId, Long wardrobeId);

    /**
     * 按一级分类统计条目数量（衣橱页分类 Tab 的角标）
     *
     * @return key = 一级分类码（未归类为 {@code UNKNOWN}），value = 数量
     */
    java.util.Map<String, Integer> countByCategory(Long userId);

    /**
     * 编辑条目（全字段，非 null 即覆盖）
     */
    WardrobeItemVO updateItem(Long userId, Long wardrobeId, UpdateWardrobeReq req);

    /**
     * 删除条目
     */
    void deleteItem(Long userId, Long wardrobeId);
}
