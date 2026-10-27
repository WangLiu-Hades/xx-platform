package com.xxplatform.api.service.task;

import com.xxplatform.api.common.Instants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 租约回收器：定期把"标着 RUNNING 但租约早过期"的任务收回。
 *
 * <p><b>为什么需要它，而不是让 worker 自己负责</b>：worker 可能被 kill -9、可能整台机器掉电，
 * 它没有机会清理自己的租约。没有 reaper 时这些任务永远停在 {@code RUNNING}，占着
 * "单账号单任务"的并发额度，也不会有人重试。
 *
 * <p><b>为什么默认关闭、只在 {@code db} profile 打开</b>：单测与骨架启动不该起后台线程，
 * 否则测试的时序不可控；而多实例部署时每个实例都会跑这个 {@code @Scheduled}，靠
 * {@code FOR UPDATE SKIP LOCKED} 互不重复——这是可以接受的，但不该在开发机默认开启。
 *
 * <p>异常必须吞掉并记日志：一次数据库抖动不该让 {@code @Scheduled} 任务永久停止
 * （Spring 的默认行为是记录后继续，但显式捕获能让日志带上"本轮失败"的语义）。
 */
@Component
@ConditionalOnProperty(prefix = "xx.task", name = "reaper-enabled", havingValue = "true")
public class TaskReaper {

    private static final Logger log = LoggerFactory.getLogger(TaskReaper.class);

    private final TaskService taskService;

    public TaskReaper(TaskService taskService) {
        this.taskService = taskService;
    }

    /** 间隔默认 30s：小于最短租约（2m）的一半，保证过期任务最多拖两轮就会被收回。 */
    @Scheduled(fixedDelayString = "${xx.task.reaper-interval:30s}")
    public void reap() {
        try {
            taskService.reapExpiredLeases(Instants.nowUtc());
        } catch (RuntimeException failure) {
            log.error("租约回收失败，本轮跳过", failure);
        }
    }
}
