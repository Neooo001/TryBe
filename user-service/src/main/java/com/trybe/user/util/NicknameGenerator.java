package com.trybe.user.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机昵称生成器 —— Web 端手机号注册时使用。
 *
 * <p>需求：首次通过手机号注册进入创作者后台时，系统随机生成一个昵称，用户可后续修改。
 * 设计上不用「用户1380013」这种暴露手机号的模板（隐私与观感都差），
 * 采用「意境前缀 + 主体 + 两位数字」组合，重复概率低且可读。
 *
 * @author TryBe Team
 */
public final class NicknameGenerator {

    private static final String[] PREFIXES = {
            "清晨的", "午后的", "雨天的", "雪后的", "黄昏的", "晴天的",
            "旧巷里的", "海边上的", "灯下的", "楼顶的", "窗边的", "远山的",
            "会发光的", "爱做梦的", "不睡觉的", "迷路的", "安静的", "慢热的"
    };

    private static final String[] NOUNS = {
            "裁缝", "旅人", "猫", "裁纸刀", "衣架", "针脚", "布丁", "云",
            "织布鸟", "纸飞机", "蒲公英", "针织帽", "帆布鞋", "纽扣", "小样",
            "试衣镜", "衣柜", "风铃", "月光", "小行星"
    };

    private NicknameGenerator() {
    }

    /**
     * 生成一个随机昵称，例如「雨天的针脚27」。
     *
     * @return 2~16 字符的昵称
     */
    public static String next() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String prefix = PREFIXES[random.nextInt(PREFIXES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        int suffix = random.nextInt(10, 100);
        return prefix + noun + suffix;
    }
}
