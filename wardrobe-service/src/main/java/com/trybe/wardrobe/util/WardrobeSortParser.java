package com.trybe.wardrobe.util;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.trybe.common.domain.PageQuery;
import com.trybe.wardrobe.domain.WardrobeSortField;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 衣柜多字段排序解析器。
 *
 * <p>把前端传来的 {@code sorts} 字符串（形如 {@code "price:desc,wear_count:desc"}）解析成
 * MyBatis-Plus 的 {@link OrderItem} 列表，供分页查询直接 {@code page.addOrder(...)} 使用。
 *
 * <p>设计要点（均为「为什么这么做」，不是「做了什么」）：
 * <ol>
 *   <li><b>空值一律沉底</b>：MySQL 没有原生 {@code NULLS LAST}，因此对可空列先追加一个
 *       {@code (col IS NULL)} 的**升序**项 —— {@code IS NULL} 结果是 0（非空）/ 1（空），
 *       升序即「非空在前、空值在后」，且与主排序方向无关，从而无论 asc 还是 desc 空值都沉底。</li>
 *   <li><b>恒定 {@code id DESC} tie-breaker</b>：id 唯一且非空，追加后整个 ORDER BY 构成**全序**，
 *       {@code LIMIT/OFFSET} 分页时同一行不会因「排序键等值 + 无稳定次序」而跨页重复或漏掉。</li>
 *   <li><b>旧参数也要过白名单</b>：{@link PageQuery#toMpPage()} 会把前端 {@code sortBy} 直接当列名
 *       拼进 ORDER BY（无任何校验），衣柜接口改用本解析器后，让旧 {@code sortBy}/{@code isAsc}
 *       也强制重走同一套白名单，等于把这条「任意列名注入」通道彻底关闭。</li>
 *   <li><b>绝不抛异常</b>：任何脏输入都只导致「丢弃该段」或「回落默认排序」，接口不能因排序参数而 500。</li>
 * </ol>
 *
 * <p>安全保证：本类**从不**把外部输入原样拼进 SQL —— 只有 {@link WardrobeSortField#getColumn()}
 * 这类枚举常量列名才会进入 ORDER BY，非法字符（分号、括号、注释等）在语法校验阶段即被整段丢弃。
 */
@Slf4j
public final class WardrobeSortParser {

    /** 最多参与的排序字段数（超出丢弃）：避免 ORDER BY 过长拖累索引选择与排序开销。 */
    public static final int MAX_SEGMENTS = 3;

    /** 恒定 tie-breaker 列：id 唯一且非空，保证排序全序、分页稳定。 */
    private static final String TIE_BREAKER_COLUMN = "id";

    /**
     * 单段语法：{@code <字段>[ : <方向> ]}。
     *
     * <p>字段必须是纯标识符（字母/下划线开头，后跟字母/数字/下划线）；方向只允许字母串（随后再判定 asc/desc）。
     * 任何夹带其它字符（空格、分号、括号、SQL 注释等）的段都会整体判定为非法并被丢弃，
     * 从而保证「外部输入的任意字符都不会以任何形式进入 SQL 文本」。
     */
    private static final Pattern SEGMENT_PATTERN =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*(?::\\s*([A-Za-z]+))?$");

    private WardrobeSortParser() {
    }

    /**
     * 解析排序参数为 ORDER BY 项列表（纯静态、不依赖 Spring 上下文，便于直接单测/main 调用）。
     *
     * <p>兜底策略：解析结果为空（输入为空 / 全是非法段）时返回默认排序 {@code create_time DESC, id DESC}；
     * 解析过程中出现任何异常时记录 warn 并返回默认排序，**绝不向上抛**。
     *
     * @param sorts     多字段排序串，形如 {@code "price:desc,wear_count:desc"}，可为空
     * @param pageQuery 旧版分页/排序参数载体，sorts 为空时回落其 {@code sortBy}/{@code isAsc}
     * @return 一定非空的 OrderItem 列表
     */
    public static List<OrderItem> parse(String sorts, PageQuery pageQuery) {
        try {
            List<OrderItem> orderItems = resolve(sorts, pageQuery);
            if (orderItems.isEmpty()) {
                return defaultOrder();
            }
            return orderItems;
        } catch (Exception e) {
            // 排序只是「展示顺序」，绝不能因为参数脏而让接口失败 —— 静默降级到默认排序
            log.warn("[Wardrobe] 排序参数解析异常，静默回落默认排序 sorts={}", sorts, e);
            return defaultOrder();
        }
    }

    /**
     * 把解析结果渲染成可直接断言的 ORDER BY 片段（仅用于验证/日志，不参与实际查询拼装）。
     *
     * <p>MyBatis-Plus 3.4.2 的 {@link OrderItem} 没有 {@code getSqlSegment()}（其 ORDER BY 由此处的
     * {@code column + 方向} 语义决定，真正拼装发生在 {@code PaginationInnerInterceptor} 内），
     * 故这里自行按同一语义渲染，避免为了复用而升级 MP 版本或新增依赖。
     */
    public static String previewSql(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("ORDER BY ");
        for (int i = 0; i < orderItems.size(); i++) {
            OrderItem item = orderItems.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(item.getColumn()).append(item.isAsc() ? " ASC" : " DESC");
        }
        return sb.toString();
    }

    // ==================== 内部实现 ====================

    private static List<OrderItem> resolve(String sorts, PageQuery pageQuery) {
        String raw = resolveRawInput(sorts, pageQuery);
        List<OrderItem> result = new ArrayList<>();
        if (StrUtil.isBlank(raw)) {
            return result;
        }

        Set<WardrobeSortField> accepted = new LinkedHashSet<>();
        for (String segment : raw.split(",")) {
            if (accepted.size() >= MAX_SEGMENTS) {
                // 段数上限：只取前 MAX_SEGMENTS 个「有效字段」，其余丢弃
                break;
            }
            String trimmed = segment == null ? "" : segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher matcher = SEGMENT_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                // 段内夹带非法字符（如 "price:desc;delete from x" 的分号/空格）→ 整段丢弃
                continue;
            }
            WardrobeSortField field = WardrobeSortField.of(matcher.group(1));
            if (field == null) {
                // 白名单未命中：形如 "id"、"(SELECT 1)"、文本列 "title" 等 → 一律丢弃
                continue;
            }
            if (!accepted.add(field)) {
                // 同一字段重复出现（如 "price:asc,price:desc"）只保留首次，避免自相矛盾的排序
                continue;
            }
            boolean asc = resolveAsc(matcher.group(2), field);
            appendField(result, field, asc);
        }

        if (!result.isEmpty()) {
            // 恒定 tie-breaker：构成全序，保证分页稳定、同值行次序确定（更新的 id 更大，排更前）
            result.add(OrderItem.desc(TIE_BREAKER_COLUMN));
        }
        return result;
    }

    /**
     * 取原始待解析串：优先 {@code sorts}；为空则回落旧参数 {@code sortBy + isAsc}。
     *
     * <p>旧参数回落是**有意为之**：让 {@link PageQuery#toMpPage()} 的「sortBy 直接当列名」注入面
     * 在衣柜接口上被彻底关掉（见类注释）。
     */
    private static String resolveRawInput(String sorts, PageQuery pageQuery) {
        if (StrUtil.isNotBlank(sorts)) {
            return sorts;
        }
        if (pageQuery != null && StrUtil.isNotBlank(pageQuery.getSortBy())) {
            Boolean isAsc = pageQuery.getIsAsc();
            boolean asc = isAsc == null || isAsc;
            return pageQuery.getSortBy().trim() + ":" + (asc ? "asc" : "desc");
        }
        return null;
    }

    /**
     * 解析方向：{@code asc}/{@code desc} 大小写不敏感；缺失或非法 → 该字段默认方向（不报错）。
     */
    private static boolean resolveAsc(String dirToken, WardrobeSortField field) {
        if (StrUtil.isBlank(dirToken)) {
            return field.isDefaultAsc();
        }
        if ("asc".equalsIgnoreCase(dirToken)) {
            return true;
        }
        if ("desc".equalsIgnoreCase(dirToken)) {
            return false;
        }
        return field.isDefaultAsc();
    }

    /**
     * 追加单个字段的 OrderItem：可空列先加「空值沉底」前置项，再加主排序项。
     */
    private static void appendField(List<OrderItem> result, WardrobeSortField field, boolean asc) {
        if (field.isNullable()) {
            // 空值一律沉底：(col IS NULL) 结果 0/1，升序即非空在前、空值在后，与主排序方向无关。
            // 必须带括号，避免与相邻 SQL 片段产生解析歧义（也便于 jsqlparser 解析）。
            result.add(OrderItem.asc("(" + field.getColumn() + " IS NULL)"));
        }
        // 非空列不加 IS NULL 项：列本身非空，加了只是徒增 SQL 文本
        result.add(asc ? OrderItem.asc(field.getColumn()) : OrderItem.desc(field.getColumn()));
    }

    /** 默认排序：{@code create_time DESC, id DESC}。 */
    private static List<OrderItem> defaultOrder() {
        List<OrderItem> list = new ArrayList<>(2);
        list.add(OrderItem.desc(WardrobeSortField.CREATE_TIME.getColumn()));
        list.add(OrderItem.desc(TIE_BREAKER_COLUMN));
        return list;
    }
}
