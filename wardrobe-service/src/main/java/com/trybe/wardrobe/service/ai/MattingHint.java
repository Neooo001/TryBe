package com.trybe.wardrobe.service.ai;

import com.trybe.common.ai.GarmentRecognizeResult;
import lombok.Getter;

/**
 * 抠图提示：告诉抠图实现「要保留什么」
 *
 * <p>两个 provider 用到的东西不一样，所以做成一个中性载体而不是散落的参数：
 * <ul>
 *   <li><b>生成式</b>（千问图像编辑）—— 用 {@link #subject} 拼提示词，如「只保留图片中的长裤」</li>
 *   <li><b>分割式</b>（百度智能抠图）—— 听不懂品类名，只能靠 {@link #bbox} 把主体范围收紧到衣服区域，
 *       从而少抠进模特身体、鞋包等无关内容</li>
 * </ul>
 *
 * <p>为什么百度要靠框：它只有「自动识别主体」和「手动框选主体」两种模式，没有「按品类分割」。
 * 自动模式下画面主体是整个人（或人+包），框选模式下它只在框内找主体 —— 恰好我们已经有
 * 视觉大模型返回的衣服 bbox，直接复用即可，不额外花钱。
 */
@Getter
public class MattingHint {

    /** 要保留的服饰品类中文名（生成式 provider 用），可为 null */
    private final String subject;

    /** 衣服主体框（归一化 0-1，分割式 provider 用），可为 null */
    private final GarmentRecognizeResult.Bbox bbox;

    private MattingHint(String subject, GarmentRecognizeResult.Bbox bbox) {
        this.subject = subject;
        this.bbox = bbox;
    }

    public static MattingHint of(String subject, GarmentRecognizeResult.Bbox bbox) {
        return new MattingHint(subject, bbox);
    }

    public static MattingHint empty() {
        return new MattingHint(null, null);
    }

    /**
     * 是否有可用的主体框（框非法时分割式 provider 会退回自动模式）
     */
    public boolean hasValidBbox() {
        return bbox != null && bbox.valid();
    }
}
