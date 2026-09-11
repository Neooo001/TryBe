package com.trybe.wardrobe.service;

import com.trybe.common.domain.PageDTO;
import com.trybe.common.domain.PageQuery;
import com.trybe.wardrobe.domain.dto.TaobaoImportReq;
import com.trybe.wardrobe.domain.vo.WardrobeItemVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 衣柜服务
 */
public interface IWardrobeService {

    /**
     * 淘宝分享链接导入
     */
    WardrobeItemVO importFromTaobao(Long userId, TaobaoImportReq req);

    /**
     * 手动添加（图片可选）
     */
    WardrobeItemVO addManual(Long userId, String title, String remark, MultipartFile image);

    /**
     * 自定义穿搭上传
     */
    WardrobeItemVO addOutfit(Long userId, String title, String remark, MultipartFile image);

    /**
     * 分页查询衣柜
     */
    PageDTO<WardrobeItemVO> listWardrobe(Long userId, String source, PageQuery pageQuery);

    /**
     * 衣柜详情
     */
    WardrobeItemVO getDetail(Long userId, Long wardrobeId);

    /**
     * 编辑条目（title/remark）
     */
    WardrobeItemVO updateItem(Long userId, Long wardrobeId, String title, String remark);

    /**
     * 删除条目
     */
    void deleteItem(Long userId, Long wardrobeId);
}
