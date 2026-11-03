package com.xxplatform.api.storage;

import java.io.InputStream;

/**
 * 文件内容存储的抽象。
 *
 * <p>数据库只存 {@code core.file_asset.storage_key}（相对路径），内容放在可替换的介质上。
 * 现在只有 {@link LocalFileStorage}（本机目录），将来换对象存储时只改这个实现，
 * 服务层与仓储层不用动。
 */
public interface FileStorage {

    /**
     * 写入一份内容。
     *
     * @param content   文件字节
     * @param extension 扩展名（含点，例如 {@code .png}）
     * @return 存储键、内容指纹与字节数
     */
    StoredObject store(byte[] content, String extension);

    /** 按存储键打开内容流；调用方负责关闭。 */
    InputStream open(String storageKey);

    /** 按存储键删除内容；不存在时静默返回（幂等）。 */
    void delete(String storageKey);

    /**
     * 存储结果。
     *
     * @param storageKey 相对存储键（{@code yyyy/MM/uuid.ext}），写入 {@code core.file_asset.storage_key}
     * @param sha256Hex  内容 SHA-256 的小写十六进制（64 字符，对上 {@code sha256 char(64)}）
     * @param byteSize   字节数，对上 {@code byte_size}
     */
    record StoredObject(String storageKey, String sha256Hex, long byteSize) {
    }
}
