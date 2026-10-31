package com.xxplatform.api.service.task;

import java.util.List;
import java.util.Map;

/**
 * 契约 {@code TaskPage}：{@code items}/{@code page}/{@code pageSize}/{@code total} 四项都必填。
 *
 * <p>为什么 {@code total} 单独查而不是用 {@code items.size()}：分页的界面要显示"共 N 条"，
 * 本页条数回答不了这个问题；两者用同一套 WHERE，数量才不会随翻页漂移。
 */
public record TaskPageView(List<Map<String, Object>> items, int page, int pageSize, long total) {
}
