package com.trybe.material.task;

import com.trybe.material.service.IMaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * <p>
 * 素材垃圾回收定时任务
 * </p>
 *
 * <h3>两个调度</h3>
 * <ul>
 *   <li>僵尸清理：每 30 分钟，清理 PENDING_UPLOAD 超 2 小时未回调的素材</li>
 *   <li>归档物理清理：每天 3 点，物理删除软删除超 7 天的素材及其校验日志、OSS 文件</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialCleanTask {

    private final IMaterialService materialService;

    /** 僵尸阈值：上传后 2 小时仍未回调 */
    private static final long ZOMBIE_THRESHOLD_HOURS = 2;
    /** 失败归档阈值：校验失败后 24 小时再软删（留足前端查询窗口） */
    private static final long FAILED_ARCHIVE_HOURS = 24;
    /** 归档保留期：软删除后 7 天 */
    private static final long ARCHIVE_RETENTION_DAYS = 7;

    /**
     * 清理上传僵尸素材（每 30 分钟）
     */
    @Scheduled(cron = "0 */30 * * * ?")
    public void cleanZombieMaterials() {
        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusHours(ZOMBIE_THRESHOLD_HOURS);
            int cleaned = materialService.cleanZombie(beforeTime);
            if (cleaned > 0) {
                log.info("[MaterialCleanTask] 僵尸清理完成 count={}", cleaned);
            }
        } catch (Exception e) {
            log.error("[MaterialCleanTask] 僵尸清理异常", e);
        }
    }

    /**
     * 归档校验失败的素材（每小时，失败超 24h 软删）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void archiveFailedMaterials() {
        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusHours(FAILED_ARCHIVE_HOURS);
            int archived = materialService.archiveFailed(beforeTime);
            if (archived > 0) {
                log.info("[MaterialCleanTask] 失败素材归档完成 count={}", archived);
            }
        } catch (Exception e) {
            log.error("[MaterialCleanTask] 失败素材归档异常", e);
        }
    }

    /**
     * 归档物理清理（每天凌晨 3 点）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void physicallyCleanArchived() {
        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusDays(ARCHIVE_RETENTION_DAYS);
            int cleaned = materialService.physicallyClean(beforeTime);
            if (cleaned > 0) {
                log.info("[MaterialCleanTask] 归档物理清理完成 count={}", cleaned);
            }
        } catch (Exception e) {
            log.error("[MaterialCleanTask] 归档物理清理异常", e);
        }
    }
}
