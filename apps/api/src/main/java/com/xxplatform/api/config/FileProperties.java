package com.xxplatform.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 受控文件（{@code core.file_asset}）的本地存储参数。
 *
 * <p><b>为什么上限分成"图片"和"导入"两个</b>：P-003-02（USER_CONFIRMED）把两类文件的上限
 * 分开签认——人脸底图/档案头像 10 MiB，数据集/设备/人员库/RAW 导入 100 MiB。单一
 * {@code max-bytes} 只能取其中一个：取 10 MiB 会把合法导入挡在门外，取 100 MiB 又让
 * 图片接口接受 10 倍于约定的文件。两个键分别对应两条产品口径。
 *
 * <p><b>为什么保留废弃的 {@link #getMaxBytes()}</b>：旧配置键是 {@code xx.file.max-bytes}，
 * 已有调用点（历史测试/运维脚本）按"单值上限"读它。方法保留只为编译与语义兼容，
 * 返回导入上限（它是两个值中较大的那个，也是旧键当初想表达的业务上限）。旧键本身已不再
 * 参与绑定：再保留 setter 会让 {@code application.yml} 里残留的 10 MiB 把导入上限
 * 悄悄压回图片档，比"键失效"危险得多——配置文件需由配置负责人在迁移时改名。
 *
 * <p><b>为什么存储根目录可配</b>：单测与验收脚本要写临时目录，生产要挂持久卷；
 * 内容不入库（{@code core.file_asset} 只存元数据 + {@code storage_key}）。
 */
@ConfigurationProperties(prefix = "xx.file")
public class FileProperties {
    /** Base64 deployment secret, at least 32 bytes; no built-in key or unsigned fallback. */
    private String downloadPermitKey = "";
    public String getDownloadPermitKey() { return downloadPermitKey; }
    public void setDownloadPermitKey(String value) { downloadPermitKey = value == null ? "" : value; }

    /** 文件内容根目录；{@code ${user.home}} 由 Spring 的占位符解析。 */
    private String storageRoot = "${user.home}/.xx-platform/files";

    /** 图片类上限，默认 10 MiB（P-003-02）。 */
    private long imageMaxBytes = 10 * 1024 * 1024;

    /** 导入类上限，默认 100 MiB（P-003-02）。 */
    private long importMaxBytes = 100 * 1024 * 1024;

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public long getImageMaxBytes() {
        return imageMaxBytes;
    }

    public void setImageMaxBytes(long imageMaxBytes) {
        this.imageMaxBytes = imageMaxBytes;
    }

    public long getImportMaxBytes() {
        return importMaxBytes;
    }

    public void setImportMaxBytes(long importMaxBytes) {
        this.importMaxBytes = importMaxBytes;
    }

    /**
     * 已废弃的单值上限，映射到导入上限。
     *
     * @deprecated 用 {@link #getImageMaxBytes()} / {@link #getImportMaxBytes()} 区分图片与导入
     */
    @Deprecated
    public long getMaxBytes() {
        return importMaxBytes;
    }
}
