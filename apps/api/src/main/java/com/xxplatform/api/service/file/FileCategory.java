package com.xxplatform.api.service.file;

import java.util.Locale;

/**
 * 受控文件分类（{@code core.file_asset.category} / 契约 {@code FileAsset.category}）。
 *
 * <p>九个值来自契约；但"能通过 {@code POST /files/uploads} 上传"的只有六个
 * （{@code UploadFileRequest.category} 的枚举）——REPORT/EXPORT 由服务端生成，
 * CAPTURE_IMAGE 由采集入库链路产生，都不接受客户端直传。这个区分放在枚举里，
 * 避免每个调用点各写一份白名单。
 */
public enum FileCategory {

    FACE_SEARCH,
    DATASET_IMPORT,
    DEVICE_IMPORT,
    PERSON_LIBRARY_IMPORT,
    PROFILE_PORTRAIT,
    REPORT,
    EXPORT,
    CAPTURE_IMAGE,
    RAW_INGESTION;

    /** 是否是客户端可上传的分类（与契约 {@code UploadFileRequest.category} 对齐）。 */
    public boolean uploadable() {
        return switch (this) {
            case REPORT, EXPORT, CAPTURE_IMAGE -> false;
            default -> true;
        };
    }

    /**
     * 解析客户端传入的分类字符串。
     *
     * <p>大小写不敏感：契约枚举是大写，但 multipart 的表单字段常被前端写成小写。
     *
     * @throws IllegalArgumentException 未知分类（由控制层翻成 400 {@code VALIDATION_ERROR}）
     */
    public static FileCategory parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("category 不能为空");
        }
        try {
            return FileCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知文件分类：" + raw, e);
        }
    }
}
