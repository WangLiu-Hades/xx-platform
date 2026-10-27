package com.xxplatform.api.service.file;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 上传白名单（媒体类型 + 扩展名 + 字节上限）的内置目录。
 *
 * <p><b>为什么内置而不是读 {@code core.upload_policy}</b>：那张表在基线里只建了结构
 * （{@code category/max_bytes/allowed_mime_types/allowed_extensions/enabled}），
 * 没有一行种子数据；{@code ClientConfigRepository.uploadPolicies()} 现在读出来是空集合。
 * 如果上传端依赖它，结局就是"所有文件都被拒"。等甲方把策略表补齐（W2 待确认事项），
 * 把 {@link #policyFor} 换成查表即可，调用方不用动。
 *
 * <p>类别与媒体的对应关系直接抄契约
 * {@code UploadFileRequest.description}："FACE_SEARCH/PROFILE_PORTRAIT 仅 JPEG/PNG，
 * 数据导入 XLSX/CSV/JSON"。
 */
public final class UploadPolicyCatalog {

    /** 图片类：人脸检索底图与档案头像。 */
    private static final Set<String> IMAGE_MIME = Set.of("image/jpeg", "image/png");
    private static final Set<String> IMAGE_EXT = Set.of(".jpg", ".jpeg", ".png");

    /** 导入类：数据集、设备、人员库、RAW 采集。 */
    private static final Set<String> TABULAR_MIME = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/csv",
            "application/json");
    private static final Set<String> TABULAR_EXT = Set.of(".xlsx", ".csv", ".json");

    private UploadPolicyCatalog() {
    }

    /**
     * 取某分类的上传策略。
     *
     * @param category 分类
     * @param maxBytes 全局字节上限（来自 {@code xx.file.max-bytes}，允许运维按部署调整）
     */
    public static UploadPolicy policyFor(FileCategory category, long maxBytes) {
        return switch (category) {
            case FACE_SEARCH, PROFILE_PORTRAIT -> new UploadPolicy(IMAGE_MIME, IMAGE_EXT, maxBytes);
            case DATASET_IMPORT, DEVICE_IMPORT, PERSON_LIBRARY_IMPORT, RAW_INGESTION ->
                    new UploadPolicy(TABULAR_MIME, TABULAR_EXT, maxBytes);
            default -> throw new IllegalArgumentException("该分类不接受客户端上传：" + category);
        };
    }

    /** 把 {@code Content-Type} 归一化：去掉 {@code ; charset=...} 等参数并转小写。 */
    public static String normalizeMime(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String base = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /** 取文件名的小写扩展名（含点）；没有扩展名返回空串。 */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    /**
     * 上传策略。
     *
     * @param mimeTypes  允许的归一化媒体类型
     * @param extensions 允许的小写扩展名（含点）
     * @param maxBytes   本策略的字节上限
     */
    public record UploadPolicy(Set<String> mimeTypes, Set<String> extensions, long maxBytes) {

        public boolean allowsMime(String normalizedMime) {
            return normalizedMime != null && mimeTypes.contains(normalizedMime);
        }

        public boolean allowsExtension(String extension) {
            return extension != null && extensions.contains(extension);
        }

        /** 便于错误信息：把白名单渲染成人类可读的逗号串。 */
        public String describe() {
            return String.join("/", mimeTypes.stream().sorted().toList());
        }
    }

    /** 仅供测试：暴露类别到扩展名的映射规模，防止误删分支。 */
    static Map<String, Set<String>> debugExtensions() {
        return Map.of(
                "image", IMAGE_EXT,
                "tabular", TABULAR_EXT);
    }
}
