package com.xxplatform.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 受控文件（{@code core.file_asset}）的本地存储参数。
 *
 * <p><b>为什么上限放在配置而不是常量</b>：{@code client-config.maxUploadBytes} 由甲方签认后改库，
 * 但上传端点必须在读请求体之前就知道上限（否则 10 MiB 的文件已经落盘才拒绝）。这里默认值与
 * 种子配置同值（10 MiB），部署时可用 {@code XX_FILE_MAX_BYTES} 覆盖；两边不一致时以本配置为准，
 * 并在 {code verify-files.ps1} 里对同一份文件做 413 断言。
 *
 * <p><b>为什么存储根目录可配</b>：单测与验收脚本要写临时目录，生产要挂持久卷；
 * 内容不入库（{@code core.file_asset} 只存元数据 + {@code storage_key}）。
 */
@ConfigurationProperties(prefix = "xx.file")
public class FileProperties {

    /** 文件内容根目录；{@code ${user.home}} 由 Spring 的占位符解析。 */
    private String storageRoot = "${user.home}/.xx-platform/files";

    /** 单文件字节上限，默认 10 MiB（与 {@code client-config.maxUploadBytes} 同值）。 */
    private long maxBytes = 10 * 1024 * 1024;

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }
}
