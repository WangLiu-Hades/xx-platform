package com.xxplatform.api.service.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.AnalysisContextRepository.ContextRow;
import com.xxplatform.api.infra.FileAssetRepository.FileAssetRow;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.CurrentDataScope;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** A file ID is a reference, never an exemption from the source domain's current authorization. */
@Component
public class FileAccessPolicy {
    private final JdbcTemplate jdbc;
    private final CurrentDataScope live;
    private final ObjectMapper mapper;

    public FileAccessPolicy(JdbcTemplate jdbc, CurrentDataScope live, ObjectMapper mapper) {
        this.jdbc = jdbc; this.live = live; this.mapper = mapper;
    }

    public void require(AuthenticatedSession session, FileAssetRow file, List<String> permissions) {
        if (!live.active(session.userId())) deny();
        switch (file.category()) {
            case "EXPORT" -> requireExport(session, file, permissions, true);
            case "REPORT" -> {
                if (!permissions.contains("report:read")) deny();
                // Report publication/range resolution belongs to the report domain; until it is
                // connected, an orphan or unverified report file is unavailable, never globally visible.
                throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "报表文件尚未接入完整来源范围校验");
            }
            case "CAPTURE_IMAGE" -> {
                if (!permissions.contains("capture:read")) deny();
                throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "采集图片尚未接入完整来源范围校验");
            }
            case "FACE_SEARCH", "PROFILE_PORTRAIT" -> { if (!permissions.contains("face:download")) deny(); }
            case "DATASET_IMPORT" -> { if (!permissions.contains("dataset:write")) deny(); }
            case "DEVICE_IMPORT" -> { if (!permissions.contains("device:write")) deny(); }
            case "PERSON_LIBRARY_IMPORT" -> { if (!permissions.contains("person-library:write")) deny(); }
            case "RAW_INGESTION" -> { if (!permissions.contains("ingestion:write")) deny(); }
            default -> deny();
        }
    }

    /** getFileAsset is AUTHENTICATED: owners can poll their uploads without download privileges. */
    public void requireMetadata(AuthenticatedSession session, FileAssetRow file, List<String> permissions) {
        if (!live.active(session.userId())) deny();
        switch (file.category()) {
            case "EXPORT" -> requireExport(session, file, permissions, false);
            case "REPORT", "CAPTURE_IMAGE" -> throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "文件尚未接入完整来源范围校验");
            default -> { /* The private upload's owner is the metadata visibility predicate. */ }
        }
    }

    private void requireExport(AuthenticatedSession session, FileAssetRow file, List<String> permissions, boolean download) {
        List<Map<String, Object>> jobs = jdbc.queryForList("""
                SELECT j.source_type,j.source_snapshot::text AS source_snapshot,t.owner_user_id,t.status
                  FROM analysis.export_job j JOIN analysis.async_task t ON t.id=j.task_id
                 WHERE j.output_file_id=? AND t.result_asset_id=j.output_file_id AND t.deleted=false
                """, file.id());
        if (jobs.isEmpty()) throw new ApiException(ErrorCode.DATA_NOT_FOUND, "文件没有已发布且可核验的来源");
        for (Map<String, Object> job : jobs) {
            if (!session.userId().equals(job.get("owner_user_id")) || !"SUCCESS".equals(job.get("status"))) continue;
            if (!"CAPTURE".equals(job.get("source_type"))) continue;
            if (download && !permissions.contains("capture:export")) deny();
            try {
                Map<String, Object> source = mapper.readValue(String.valueOf(job.get("source_snapshot")),
                        new com.fasterxml.jackson.core.type.TypeReference<>() { });
                Map<String, Object> filter = CurrentDataScope.object(source.get("filterSnapshot"));
                if (filter.isEmpty() || !(source.get("permissionVersion") instanceof Number version)) continue;
                ContextRow frozen = new ContextRow(UUID.fromString(String.valueOf(source.get("contextId"))),
                        session.userId(), String.valueOf(source.getOrDefault("sourceModule", "M06")),
                        String.valueOf(source.get("filterType")), ((Number) source.get("filterSchemaVersion")).longValue(),
                        mapper.writeValueAsString(filter), version.longValue(), OffsetDateTime.MAX);
                // File retention is independent of the original Context's 30-minute access TTL.
                // A broad frozen query may contain sites no longer authorized. Without a complete
                // result footprint, changed authorization requires regeneration, not guessing that
                // the existing immutable file contains only today's narrower intersection.
                if (live.contextAllowed(session.userId(), frozen)) return;
            } catch (java.io.IOException | RuntimeException invalid) {
                if (invalid instanceof ApiException api) throw api;
                // Unverifiable provenance is rejected without exposing its data.
            }
        }
        throw new ApiException(ErrorCode.PERMISSION_DATA_SCOPE, "文件来源不在当前授权范围内");
    }

    private static void deny() { throw new ApiException(ErrorCode.PERMISSION_DENIED, "缺少文件来源业务的当前访问权限"); }
}
