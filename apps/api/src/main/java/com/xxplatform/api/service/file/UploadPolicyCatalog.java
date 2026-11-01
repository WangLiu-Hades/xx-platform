package com.xxplatform.api.service.file;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 上传白名单（媒体类型 + 扩展名 + 字节上限 + 行数上限）的内置目录。
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
 *
 * <p>字节上限按 P-003-02 分成图片（10 MiB）与导入（100 MiB）两档，具体值来自
 * {@code xx.file.*} 配置；这里的常量只固定"哪些分类用哪一档"和导入的行数上限。
 */
public final class UploadPolicyCatalog {

    /**
     * 导入类单文件最多行数（P-003-02：普通导入 10 万行）。
     *
     * <p>本轮只在策略里暴露这个值，不在上传时逐行计数——行数校验属于导入任务（M18 之后
     * 的 M18-ish 工作），那里才有"第几行错、错在哪个字段"的上下文；上传端点只负责
     * 文件级的上限。
     */
    public static final int IMPORT_MAX_ROWS = 100_000;

    /** 图片类不按行计，0 表示"该分类没有行上限这一维"。 */
    public static final int NO_ROW_LIMIT = 0;

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
     * @param category       分类
     * @param imageMaxBytes  图片类字节上限（{@code xx.file.image-max-bytes}，默认 10 MiB）
     * @param importMaxBytes 导入类字节上限（{@code xx.file.import-max-bytes}，默认 100 MiB）
     */
    public static UploadPolicy policyFor(FileCategory category, long imageMaxBytes, long importMaxBytes) {
        if (imageMaxBytes <= 0 || importMaxBytes <= 0) {
            throw new IllegalArgumentException("上传字节上限必须为正");
        }
        return switch (category) {
            case FACE_SEARCH, PROFILE_PORTRAIT ->
                    new UploadPolicy(IMAGE_MIME, IMAGE_EXT, imageMaxBytes, NO_ROW_LIMIT);
            case DATASET_IMPORT, DEVICE_IMPORT, PERSON_LIBRARY_IMPORT, RAW_INGESTION ->
                    new UploadPolicy(TABULAR_MIME, TABULAR_EXT, importMaxBytes, IMPORT_MAX_ROWS);
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
     * @param maxRows    本策略的行数上限；0 = 该分类不按行计（图片）
     */
    public record UploadPolicy(Set<String> mimeTypes, Set<String> extensions, long maxBytes, int maxRows) {

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
