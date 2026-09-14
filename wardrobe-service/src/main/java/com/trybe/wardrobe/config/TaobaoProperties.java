package com.trybe.wardrobe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "taobao.tbk")
public class TaobaoProperties {

    /** 淘宝开放平台应用 AppKey */
    private String appKey;

    /** 应用 AppSecret */
    private String appSecret;

    /** 淘宝联盟广告位ID（转链/淘口令类接口必填，商品详情接口不需要） */
    private Long adzoneId;

    /** 网关地址 */
    private String endpoint = "https://eco.taobao.com/router/rest";

    /**
     * 是否启用 taobao.tbk.tpwd.parse 作为商品ID解析兜底。
     * 该接口属「淘口令解析&转链」权限包，官方不默认开放；未开通时保持 false，避免无效调用消耗日调用量。
     */
    private boolean tpwdParseEnabled = false;

    /**
     * 淘宝导入时最多转存的商品图数量（主图 + 详情图），控制导入耗时与 OSS 占用
     */
    private int maxDetailImages = 5;
}