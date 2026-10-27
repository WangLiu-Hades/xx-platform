package com.xxplatform.api.storage;

import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.FileProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 本机目录存储：{@code <storageRoot>/yyyy/MM/<uuid>.<ext>}。
 *
 * <p><b>为什么按日期分目录</b>：单目录几十万文件后，文件系统的目录项查找会明显变慢；
 * 按年月分桶能把单目录规模压到可接受范围，也方便按时间归档/清理。
 *
 * <p><b>为什么存储键要做路径校验</b>：{@code storage_key} 来自数据库，但数据库内容
 * 也可能被误写或被攻击者间接控制（例如未来支持"另存为"）。一律拒绝绝对路径、
 * {@code ..} 段与解析后逃逸出根目录的键，否则 {@code open()} 就是任意文件读取。
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path root;

    public LocalFileStorage(FileProperties properties) {
        this.root = Paths.get(properties.getStorageRoot()).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(byte[] content, String extension) {
        if (content == null) {
            throw new IllegalArgumentException("content 不能为空");
        }
        String normalizedExtension = normalizeExtension(extension);
        String storageKey = LocalDate.now(ZoneOffset.UTC).format(MONTH)
                + "/" + UUID.randomUUID() + normalizedExtension;
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            // CREATE_NEW：键里有 UUID，撞键说明随机数或时钟出了问题，宁可报错也不要覆盖别人的文件
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("文件写入失败：" + storageKey, e);
        }
        return new StoredObject(storageKey, Sha256.hex(Sha256.ofBytes(content)), content.length);
    }

    @Override
    public InputStream open(String storageKey) {
        Path target = resolve(storageKey);
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new IllegalStateException("文件读取失败：" + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolve(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("文件删除失败：" + storageKey, e);
        }
    }

    /** 把相对存储键解析成根目录内的绝对路径，并阻止目录穿越。 */
    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey 不能为空");
        }
        Path candidate = Paths.get(storageKey);
        if (candidate.isAbsolute() || storageKey.contains("..")) {
            throw new IllegalArgumentException("非法存储键：" + storageKey);
        }
        Path resolved = root.resolve(candidate).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("存储键逃逸出根目录：" + storageKey);
        }
        return resolved;
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String value = extension.trim().toLowerCase(Locale.ROOT);
        return value.startsWith(".") ? value : "." + value;
    }
}
