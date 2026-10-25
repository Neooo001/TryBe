# 衣橱图片改「抠图 + 白底留图」实施方案

> 目标：导入的衣物图只保留衣物本身（背景/模特身体/鞋包一律去掉），其余留白 —— **纯白底 JPEG**。
> 口径：**存量旧条目不批量重抠**，只对以后新导入的生效。
>
> **状态：代码已实施（2026-10-24），默认走百度智能抠图，产物为白底 JPEG，待配密钥 + 真机验收。**
>
> 变更史：透明底 → **白底**（2026-10-24 17:40，用户改的口径）。

---

## 0. 为什么不是阿里云的分割抠图

最初方案是阿里云视觉智能开放平台的 `SegmentCloth`（服饰分割），**该产品已退市**：

| 时间 | 事项 |
| --- | --- |
| 2026-09-16 | 阿里云发布《视觉智能开放平台启动退市通知》 |
| 2026-10-11 | 停止新用户开通调用与购买 ← 已过，新账号开不了 |
| 2027-04-11 | 停止老用户购买与续费 |
| 2028-04-30 | 全面停止服务 |

替代：百度智能云「智能抠图」（0.03 元/次，个人实名 100 次免费）。

---

## 1. 原实现为什么做不到「只留衣服」

| 环节 | 文件 | 现状 |
| --- | --- | --- |
| 裁剪 | `GarmentImageCropService#cropToGarment` | 只按 AI 的 bbox 切矩形 + 4% 留白 → 背景原样保留 |
| 抠图 | `GarmentMattingService` 实现 | `enabled=false` 且无密钥 → Bean 不创建 → 从未执行 |
| 结果 | `WardrobeImportProcessor#tryMatting` | 永远返回 null，只剩 bbox 裁切在跑 |

---

## 2. 当前实现：百度智能抠图 + 本地合成白底

```
原图(OSS) → 读字节 → 缩放到 128~3000px / base64 ≤10MB
          → POST image-process/v1/segment（method=control + AI的bbox，return_form=rgba）
          → 返回「透明前景 PNG」
          → WhiteBackdrop 合成纯白底 + 压到长边 1600 + 编码 JPEG
          → OSS import/{taskNo}/matting.jpg → task.cutout_object_key
```

### 关键 1：用 `method=control` + AI 的 bbox 把范围收紧到衣服

百度没有「按品类分割」，只有两种模式：

| 模式 | 行为 | 问题 |
| --- | --- | --- |
| `method=auto` | 自动找画面主体 | 真人穿着时**主体是整个人** → 连腿、鞋、包一起抠 |
| `method=control` | **只在给定矩形框里找主体** | 框要自己给，且不能贴边、要 >10×10 |

把视觉大模型已返回的**衣服 bbox** 换算成像素框（`[[[x1,y1],[x2,y2]]]`）传进去，框外的腿、
鞋、包自然排除。**零额外成本**，只是把已有数据用起来。bbox 无效 / 尺寸未知时退回 `auto`。

### 关键 2：服务端只给「透明前景」，白底得自己合成

百度 `return_form` 只有 `rgba` / `mask`，**没有 whiteBK 这种白底参数**（那是已退市的阿里云分割抠图才有的）。
所以透明 → 白底必须自己做：新建 RGB 画布铺白 → 叠上带 alpha 的前景，alpha 混合会自动处理边缘过渡。
实现在 `WhiteBackdrop`，复用 `ImageUtils.resize`（它内部就是「TYPE_INT_RGB 画布 + 填白 + drawImage」）。

### 关键 3：必须裁掉四周白边，否则卡片里的衣服很小

抠图服务**保留原图整块画布**：原图 1080×2374，输出还是 1080×2374，只是背景变白。
卡片是 `mode="aspectFit"`（完整显示不裁切）→ 整块画布被塞进卡片，衣服只占中间一小条，
一条长裤看着像根筷子。所以合成白底后按内容包围盒裁一刀，让画面紧贴衣服。

**找边界必须用 alpha，不能用「白不白」**：衣橱里全是白衬衫、米色针织、浅灰卫衣，
白衣服在白底上会被判成「没有内容」，直接裁成空图。抠图返回的 alpha 通道才是真正的前景轮廓，
与颜色无关；只有输入本身没有 alpha（白底 JPEG）时才退回白度扫描。

裁切后四周补 4% 留白（与 `GarmentImageCropService` 的 `PADDING_RATIO` 一致），避免衣服贴边显得局促。

### 关键 4：白底之后改存 JPEG，体积降一个数量级

| 产物 | 体积 | 说明 |
| --- | --- | --- |
| 之前：原分辨率无损 PNG | 0.6 ~ 2.8 MB | 衣橱一次 12 张 → 十几 MB，必卡 |
| 现在：白底 JPEG（长边 1600，已去白边） | 约 60 ~ 250 KB | 白底不再需要 alpha，PNG 是纯浪费 |

配置项 `trybe.ai.matting.white-max-side`（默认 1600）可调；`≤0` 用默认值。

### 仍做不到的
- 裙摆里的腿、领口内的皮肤这类**与衣服重叠**的部分，框选也切不掉
- 手持的包、搭在椅子上的衣服，若落在框内仍会保留

后路：切 `provider: qwen-image-edit`（能点名品类），或改走本地开源服饰分割。

---

## 3. 两个 provider 怎么切

| 值 | 实现类 | 特点 |
| --- | --- | --- |
| `baidu`（默认） | `BaiduImageMattingService` | 0.03 元/次、不重绘、不能点名品类，靠 bbox 框选 |
| `qwen-image-edit` | `QwenImageEditMattingService` | 0.1 元/张、能点名品类、可能重绘 |

两者都实现 `GarmentMattingService`，靠 `@ConditionalOnExpression` 二选一，业务层零改动；
**两条路径的产物都会过一遍 `WhiteBackdrop`**，输出口径统一为白底 JPEG。

---

## 4. 代码改动清单（无 DDL）

| 文件 | 改动 |
| --- | --- |
| `service/ai/GarmentMattingService.java` | 接口：`matting(originKey, userId, taskNo, MattingHint)` |
| `service/ai/MattingHint.java` | **新增**：中性提示载体（`subject` 给生成式、`bbox` 给分割式） |
| `service/ai/BaiduImageMattingService.java` | **新增**：百度智能抠图 + bbox 框选 + 白底合成 |
| `service/ai/QwenImageEditMattingService.java` | 备选 provider；提示词改为「纯白背景」，产物同样过白底合成 |
| `service/ai/WhiteBackdrop.java` | **新增**：透明前景 → 白底 JPEG（顺便压长边） |
| `service/ai/AliyunImageSegMattingService.java` | **已删除**（产品退市） |
| `service/ai/GarmentClothClassResolver.java` | **已删除**（ClothClass 只对退市接口有意义） |
| `service/WardrobeImportProcessor.java` | `tryMatting(task, result)` + `subjectOf(result)`，bbox 装进 `MattingHint` |
| `config/GarmentMattingProperties.java` | `enabled`/`provider`/`timeout-ms`/`max-bytes`/`white-max-side` + `baidu.*` / `qwen.*` |
| `util/WardrobeOssPathBuilder.java` | `importMatting` → `matting.jpg`；`isMattingCutout` 兼容历史 `.png`（短暂透明期产物） |
| `src/main/resources/application.yaml` | 同步配置，`enabled: false` |

### 不改的东西
- 不加数据库列（靠产物文件名区分 `matting.jpg` / `cutout.jpg`）
- 不动 commit 语义（`image_key = cutout_object_key` 原样透传）
- 不动存量数据

---

## 5. 验收与回滚

编译：
```
JAVA_HOME=D:\Idea\JDK\jdk17 ~/.m2/repository/../wrapper/dists/apache-maven-3.9.16-bin/*/bin/mvn.cmd -s D:\maven\.m2\settings.xml -pl wardrobe-service -am -DskipTests -B compile
```

功能验（真实服务，本机无法自证）：
1. 三张图各导入一次：连衣裙（模特+灰背景）、长裤（带鞋）、鞋（水泥地）
2. 日志里应出现 `[GarmentMatting] 使用主体框收紧范围 ... box=(x1,y1)(x2,y2)` 与
   `[WhiteBackdrop] 白底合成完成 ... -> xxx bytes`
3. `cutout_object_key` 应以 `/matting.jpg` 结尾，且产物 100~250KB 量级
4. 卡片：只剩衣服、背景纯白
5. 故意配错密钥 → 导入仍成功，降级为 `cutout.jpg`

**回滚**：Nacos 里 `enabled: false` 立刻回到 bbox 裁切，无需回滚代码。

---

## 6. 视觉提醒（白底 vs 卡片底色）

衣橱卡片的图片区底色是 `--surface-sunk`（浅灰）。白底图放上去会**显出一块白色矩形**。
若想让它融成一片，把 `pages/wardrobe/wardrobe.wxss` 里 `.card-img-wrap` 的 `background`
改成 `#fff` 即可（1 行，纯视觉）。这一步尚未改动，需要时再说。

---

## 7. Nacos 配置（`trybe-wardrobe-dev.yaml`）

```yaml
trybe:
  ai:
    matting:
      enabled: true
      provider: baidu
      timeout-ms: 30000
      max-bytes: 5242880
      white-max-side: 1600      # 白底图长边上限，越小越快
      baidu:
        api-key: <百度智能云应用 API Key>
        secret-key: <同一应用 Secret Key>
        return-form: rgba       # 服务端只给 rgba，白底由本地合成
        refine-mask: true
        method: auto            # 有可用 bbox 时代码会自动改走 control
```

⚠️ `bootstrap*.yaml` / `application*.yaml` 被 `.gitignore` 忽略，本地配置改动**没有 git 版本可回滚**，动前先备份。

---

## 8. 实施记录（2026-10-24）

### 已验证（共 58 条断言，全过）
- `mvn -pl wardrobe-service -am -DskipTests compile` → **BUILD SUCCESS**
- 百度入参 10 条：小图最短边抬到 128、4000px 压到 3000、800×1200 原样、base64 无 `data:` 前缀、缺密钥抛异常
- bbox→position 10 条：像素换算、贴边内缩 2px、越界/退化框退回 auto、尺寸未知退回 auto、三层数组结构
  - 抓到一个真 bug：尺寸未知时 `clamp` 边界倒挂会生成 `[[[2,2],[14,14]]]` 假框，等于把抠图锁死在左上角 → 已加尺寸前置校验
- 白底 + 去白边 20 条：
  - 透明角变纯白、内容色保留、比例不变、long side 压到 1600
  - 全白图不裁（不产出空图）、全内容图不裁、无 alpha 的 JPEG 恒等通过、不可解码字节原样返回
  - **白色衣服（#FFFFFF）仍能被 alpha 正确裁出** —— 这正是「按白度扫描」会踩的坑
  - 极窄长条（140×1500 裤子）：1080×2374 画布 → 728×1600 → 裁到 104×1094，比例保持
- 千问实现 18 条回归：提示词含「纯白/#FFFFFF」、请求体 `prompt_extend=false`、同步/异步解析

### 未验证
云端真实出图、`control` 模式下的实际抠图质量、白底边缘是否有残留色块、小程序渲染。

### 待办
1. 百度智能云控制台建应用（图像增强与特效 → 智能抠图），拿 API Key / Secret Key
2. 按第 7 节改 Nacos，`enabled: true`
3. 重启 wardrobe-service，按第 5 节验收
4. 若重叠部分（裙摆里的腿）仍保留 → 切 `provider: qwen-image-edit` 对比，或走本地开源分割

### ⚠️ 存量条目不自动更新
去白边是**导入时**做的，已经入库的旧条目仍是老图（原画布 + 大片白边），在卡片里照样显得小。
要让它变正常，只有两条路：
- 删掉该条目重新导入（消耗 1 次抠图额度）
- 用 `origin_image_key` 重跑一次抠图（需要单独脚本 + 会写库，得先确认）

