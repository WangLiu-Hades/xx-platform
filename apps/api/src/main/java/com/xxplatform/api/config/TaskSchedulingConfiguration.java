package com.xxplatform.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 打开 {@code @Scheduled}（任务租约回收用），但只在显式启用 {@code xx.task.reaper-enabled} 时。
 *
 * <p><b>为什么不在 {@code ApiApplication} 上直接写 {@code @EnableScheduling}</b>：
 * {@code ApiApplicationTests} 会启动完整上下文（用占位数据源、关掉 Flyway），一旦调度器被打开，
 * 后台线程就会按固定间隔去连一台不存在的数据库。测试本身只跑一两秒，多半来不及触发，
 * 但"多半"不是保证——某个慢环境下它就会在日志里刷连接超时，甚至把断言之外的线程异常抛到
 * 控制台，让一次正常的构建看起来像坏了。把开关放在配置里，测试与单机调试默认安静，
 * 只有 {@code db} profile（真实数据库）才启用。
 *
 * <p>{@link TaskReaper} 自身也带同样的条件，避免"调度关了但 reaper bean 还在"的歧义。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "xx.task", name = "reaper-enabled", havingValue = "true")
public class TaskSchedulingConfiguration {
}
