package com.trybe.wardrobe.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 衣服导入任务VO
 *
 * <p>前端上传后凭 taskId 轮询本对象：
 * <ul>
 *   <li>status = 0/1 → 继续轮询（展示「AI 识别中」）</li>
 *   <li>status = 2 → 展示 {@link #item} 让用户确认/编辑</li>
 *   <li>status = 3 → 展示 failReason，并提供「重新识别」入口</li>
 * </ul>
 */
@Data
@ApiModel(description = "衣服导入任务")
public class ImportTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 抠图模式：像素级抠图（白底留图） */
    public static final String CUTOUT_MODE_MATTING = "MATTING";
    /** 抠图模式：本地 bbox 矩形裁剪 */
    public static final String CUTOUT_MODE_CROP = "CROP";
    /** 抠图模式：未处理，直接使用原图 */
    public static final String CUTOUT_MODE_ORIGIN = "ORIGIN";

    @ApiModelProperty("任务ID")
    private Long taskId;

    @ApiModelProperty("任务业务号")
    private String taskNo;

    @ApiModelProperty("任务状态：0-待处理, 1-处理中, 2-成功, 3-失败")
    private Integer status;

    @ApiModelProperty("状态文案")
    private String statusText;

    @ApiModelProperty("生成的衣柜条目ID（草稿）")
    private Long wardrobeId;

    @ApiModelProperty("原图临时访问URL")
    private String originImageUrl;

    @ApiModelProperty("AI裁剪图临时访问URL")
    private String cutoutImageUrl;

    @ApiModelProperty("抠图模式：MATTING-已抠图, CROP-AI裁剪, ORIGIN-原图（前端角标文案据此区分）")
    private String cutoutMode;

    @ApiModelProperty("AI识别结果草稿（含全部可编辑属性）")
    private WardrobeItemVO item;

    @ApiModelProperty("失败原因")
    private String failReason;

    @ApiModelProperty("处理耗时(毫秒)")
    private Long costMs;
}
