package com.xxplatform.api.api;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.IdempotencyKeyHeader;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.infra.FileAssetRepository.FileAssetRow;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.SessionAuthenticator;
import com.xxplatform.api.service.file.FileService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 受控文件三端点（契约 {@code POST /files/uploads}、{@code GET /files/{id}}、
 * {@code GET /files/{id}/download}）。
 *
 * <p>上传成功返回 <b>202</b> 而不是 201：文件先落 PROCESSING、扫描结论在响应前已写入，
 * 语义是"受理完成"而非"资源创建完成"（与契约一致）。下载响应强约束：
 * {@code Content-Disposition: attachment} + RFC 5987 {@code filename*}（中文名不乱码）、
 * {@code Cache-Control: no-store}（下载内容可能含敏感数据，不允许任何中间层缓存）、
 * {@code X-Content-Type-Options: nosniff}（防止浏览器把 octet-stream 嗅探成可执行内容）。
 *
 * <p>CSRF：本控制器不再手写校验，{@code common/CsrfFilter} 已对所有写请求统一拦截；
 * 认证与幂等键形状校验仍在这里做，凡是 4xx 都必须早于读取文件体（避免白传 10 MiB）。
 */
@RestController
public class FileController {

    private static final String MULTIPART = MediaType.MULTIPART_FORM_DATA_VALUE;

    private final SessionAuthenticator sessionAuthenticator;
    private final FileService fileService;

    public FileController(SessionAuthenticator sessionAuthenticator, FileService fileService) {
        this.sessionAuthenticator = sessionAuthenticator;
        this.fileService = fileService;
    }

    @PostMapping(path = "/files/uploads", consumes = MULTIPART)
    public ResponseEntity<ApiResponses> upload(
            @RequestParam("category") String category,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String idempotencyKey,
            HttpServletRequest request) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        IdempotencyKeyHeader.require(idempotencyKey);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.SYSTEM_INTERNAL, "读取上传内容失败");
        }
        FileService.Uploaded uploaded = fileService.upload(session, category,
                file.getOriginalFilename(), file.getContentType(), content,
                RequestIdFilter.currentTraceIdOrFallback());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileAssetId", uploaded.fileAssetId().toString());
        data.put("status", uploaded.status());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback()));
    }

    @GetMapping("/files/{fileAssetId}")
    public ApiResponses get(@PathVariable UUID fileAssetId, HttpServletRequest request) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        FileAssetRow row = fileService.get(session, fileAssetId);
        return ApiResponses.success(view(row), RequestIdFilter.currentTraceIdOrFallback());
    }

    @GetMapping("/files/{fileAssetId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID fileAssetId, HttpServletRequest request) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        FileService.Download download = fileService.download(session, fileAssetId);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, download.mimeType());
        headers.setContentLength(download.byteSize());
        headers.set(HttpHeaders.CONTENT_DISPOSITION, attachment(download.fileName()));
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        InputStream stream = download.stream();
        Resource body = new InputStreamResource(stream);
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    /** 契约 FileAsset 视图（只暴露契约声明的字段）。 */
    private static Map<String, Object> view(FileAssetRow row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileAssetId", row.id().toString());
        data.put("category", row.category());
        data.put("fileName", row.fileName());
        data.put("mimeType", row.mimeType());
        data.put("byteSize", row.byteSize());
        data.put("status", row.status());
        data.put("createdAt", Instants.iso(row.createdAt()));
        if (row.expiresAt() != null) {
            data.put("expiresAt", Instants.iso(row.expiresAt()));
        }
        return data;
    }

    /**
     * RFC 5987 的 {@code filename*}：中文/空格/特殊字符都按 UTF-8 百分号编码。
     *
     * <p>{@code URLEncoder} 把空格编成 {@code +}，在 {@code filename*} 里必须还原成
     * {@code %20}，否则浏览器会把文件名里的空格显示成加号。
     */
    static String attachment(String fileName) {
        String encoded = URLEncoder.encode(fileName == null ? "download" : fileName,
                StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
