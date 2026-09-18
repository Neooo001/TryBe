package com.trybe.wardrobe.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 衣服识别大模型返回结果
 *
 * <p>对应提示词中约定的 JSON 结构，字段命名与 {@code wardrobe_item} 列一一映射，
 * 便于直接落库而无需额外转换层。
 */
@Data
@ApiModel(description = "衣服AI识别结果")
public class GarmentRecognizeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("一级分类码：TOP/BOTTOM/SET/SHOES/BAG/ACCESSORY/DECOR/OTHER")
    private String categoryL1;

    @ApiModelProperty("一级分类中文名")
    private String categoryL1Name;

    @ApiModelProperty("二级分类，如 长裤")
    private String categoryL2;

    @ApiModelProperty("分类全称，如 下装-长裤")
    private String categoryName;

    @ApiModelProperty("AI 生成的标题，如 灰色直筒休闲长裤")
    private String title;

    @ApiModelProperty("主色中文名")
    private String color;

    @ApiModelProperty("主色 HEX")
    private String colorHex;

    @ApiModelProperty("颜色列表")
    private List<String> colors;

    @ApiModelProperty("适用季节码列表：SPRING/SUMMER/AUTUMN/WINTER")
    private List<String> seasons;

    @ApiModelProperty("主风格")
    private String style;

    @ApiModelProperty("风格标签")
    private List<String> styles;

    @ApiModelProperty("材质")
    private String material;

    @ApiModelProperty("适用场合")
    private List<String> occasions;

    @ApiModelProperty("版型")
    private String fit;

    @ApiModelProperty("图案")
    private String pattern;

    @ApiModelProperty("厚薄：THIN/MEDIUM/THICK")
    private String thickness;

    @ApiModelProperty("保暖度 1-5")
    private Integer warmth;

    @ApiModelProperty("正式度 1-5")
    private Integer formality;

    @ApiModelProperty("标签")
    private List<String> tags;

    @ApiModelProperty("识别置信度 0-1")
    private Double confidence;

    @ApiModelProperty("服装主体框（归一化坐标 0-1），用于智能裁剪")
    private Bbox bbox;

    @ApiModelProperty("模型原始返回JSON")
    private String rawJson;

    /**
     * 归一化主体框
     */
    @Data
    @ApiModel("归一化包围框")
    public static class Bbox implements Serializable {

        private static final long serialVersionUID = 1L;

        private Double x1;
        private Double y1;
        private Double x2;
        private Double y2;

        /**
         * 判断是否为可用的有效框（坐标合法且面积足够，避免 AI 返回退化框导致裁剪出空图）
         */
        public boolean valid() {
            if (x1 == null || y1 == null || x2 == null || y2 == null) {
                return false;
            }
            if (x1 < 0 || y1 < 0 || x2 > 1 || y2 > 1) {
                return false;
            }
            if (x2 - x1 < 0.08 || y2 - y1 < 0.08) {
                return false;
            }
            return true;
        }
    }
}
