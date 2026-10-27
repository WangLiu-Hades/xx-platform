package com.xxplatform.api.service.plate;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.CaptureSearchResponse;
import com.xxplatform.api.generated.model.CaptureDetail;
import com.xxplatform.api.generated.model.PlateCaptureRecord;
import com.xxplatform.api.generated.model.GeoFeatureCollection;
import com.xxplatform.api.service.ingestion.DataApiException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Week-2 PLATE orchestration. No fixed identity, SQL, session store or fake results. */
public final class PlateQueryService {
    private final ReadAccess access;
    private final QueryGateway gateway;
    public PlateQueryService(ReadAccess access, QueryGateway gateway) {
        this.access = Objects.requireNonNull(access); this.gateway = Objects.requireNonNull(gateway);
    }
    public CaptureSearchResponse search(String csrf, CaptureQuery query) {
        if (csrf == null || csrf.isBlank() || csrf.length() < 16) throw invalid("xCSRFToken", "Token must contain at least 16 characters");
        Objects.requireNonNull(query);
        var authority = Objects.requireNonNull(access.requireAccess("capture:read", csrf));
        if (!"PLATE".equals(query.dataType())) throw unavailable("The requested source adapter is not connected");
        budget(query.from(), query.to(), authority.budget());
        var page = Objects.requireNonNull(gateway.search(authority, query, authority.budget().timeout()));
        if (page.getItems() == null || page.getItems().size() > query.pageSize()
                || !Integer.valueOf(query.pageSize()).equals(page.getPageSize())) {
            throw unavailable("Query adapter returned an invalid page");
        }
        return page;
    }
    public CaptureDetail get(UUID captureId) {
        if (captureId == null) throw invalid("captureId", "captureId is required");
        var authority = Objects.requireNonNull(access.requireAccess("capture:read", null));
        var detail = Objects.requireNonNull(gateway.get(authority, captureId, authority.budget().timeout()));
        if (!(detail.getRecord() instanceof PlateCaptureRecord record) || !captureId.equals(record.getCaptureId())) {
            throw unavailable("PLATE detail adapter returned an invalid record");
        }
        return detail;
    }
    public GeoFeatureCollection map(MapQuery query) {
        Objects.requireNonNull(query);
        var authority = Objects.requireNonNull(access.requireAccess("map:read", null));
        if (!"PLATE".equals(query.dataType()) || "SITE".equals(query.layer())) {
            throw unavailable("All-source and SITE map adapters are not connected");
        }
        if (query.from() != null && query.to() != null) budget(query.from(), query.to(), authority.budget());
        var collection = Objects.requireNonNull(gateway.map(authority, query, authority.budget().timeout()));
        if (collection.getFeatures() == null || collection.getFeatures().size() > query.limit()) {
            throw new DataApiException(ErrorCode.DATA_QUERY_TOO_BROAD, null,
                    "Map result exceeds the requested limit; aggregate or request tiles");
        }
        return collection;
    }
    private static void budget(OffsetDateTime from, OffsetDateTime to, QueryBudget budget) {
        if (Duration.between(from.toInstant(), to.toInstant()).compareTo(budget.maxWindow()) > 0) {
            throw new DataApiException(ErrorCode.DATA_QUERY_TOO_BROAD, "timeRange", "Query exceeds the configured window budget");
        }
    }
    private static DataApiException invalid(String field, String reason) {
        return new DataApiException(ErrorCode.VALIDATION_ERROR, field, reason);
    }
    private static DataApiException unavailable(String message) {
        return new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE, null, message);
    }
    /** Current SID/Origin/CSRF for POST, operation permission and fresh tuple scope. */
    @FunctionalInterface public interface ReadAccess {
        Authority requireAccess(String permission, String csrfToken);
    }
    /**
     * DEV-04 adapter must preserve the trusted tuple predicate (no Cartesian
     * expansion), resolve exact raw identifiers through the approved target service,
     * apply all filters, [from,to), stable sort plus captureId, and SQL budgets.
     * Cursors bind actor/scope/filter/order/pageSize and a retained snapshot session;
     * do not reopen a fresh DB snapshot per page. Check expiry, scope changes,
     * cancellation and bounded session cleanup. Propagate query timeouts and errors,
     * never a successful partial result. Opaque cursor validation belongs here.
     * Map must intersect Context/full frozen query with current scope and explicit
     * filters, resolve optional time bounds through trusted policy, use [lon,lat],
     * exclude unknown coordinates, and aggregate rather than silently truncate.
     * Return contract-valid masked PLATE DTOs. This port is not a ready HTTP service.
     */
    public interface QueryGateway {
        CaptureSearchResponse search(Authority authority, CaptureQuery query, Duration timeout);
        // Missing/deleted/invisible captures all produce DATA_NOT_FOUND under fresh scope.
        CaptureDetail get(Authority authority, UUID captureId, Duration timeout);
        GeoFeatureCollection map(Authority authority, MapQuery query, Duration timeout);
    }
    public record ScopeTuple(String dataType, UUID siteId, boolean ownOnly) {
        public ScopeTuple { Objects.requireNonNull(dataType); Objects.requireNonNull(siteId); }
    }
    public record QueryBudget(Duration maxWindow, Duration timeout) {
        public QueryBudget {
            Objects.requireNonNull(maxWindow); Objects.requireNonNull(timeout);
            if (maxWindow.isNegative() || maxWindow.isZero() || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Explicit positive server budgets are required");
            }
        }
    }
    public record Authority(UUID principalId, String scopeVersion, List<ScopeTuple> grants, QueryBudget budget) {
        public Authority {
            Objects.requireNonNull(principalId); Objects.requireNonNull(scopeVersion);
            grants = List.copyOf(grants); Objects.requireNonNull(budget);
        }
    }
    public record CaptureQuery(String dataType, OffsetDateTime from, OffsetDateTime to,
            List<UUID> siteIds, List<UUID> targetIds, List<UUID> deviceIds, String cursor,
            int pageSize, String sortBy, String sortOrder, Map<String, String> exactFilters) {
        public CaptureQuery {
            siteIds = siteIds == null ? null : List.copyOf(siteIds);
            targetIds = targetIds == null ? null : List.copyOf(targetIds);
            deviceIds = deviceIds == null ? null : List.copyOf(deviceIds);
            exactFilters = Map.copyOf(exactFilters);
        }
        @Override public String toString() { return "CaptureQuery[redacted]"; }
    }
    public record Bounds(double minLon, double minLat, double maxLon, double maxLat) {}
    public record MapQuery(Bounds bounds, int zoom, int limit, UUID contextId,
            OffsetDateTime from, OffsetDateTime to, String dataType, UUID siteId,
            String layer, String aggregation, List<UUID> siteIds, List<UUID> deviceIds, String regionCode) {
        public MapQuery {
            siteIds = siteIds == null ? null : List.copyOf(siteIds);
            deviceIds = deviceIds == null ? null : List.copyOf(deviceIds);
        }
    }
    public static PlateQueryService unavailable() {
        return new PlateQueryService((permission, csrf) -> {
            throw unavailable("Trusted PLATE permissions, query sessions and map adapters are not connected");
        }, new QueryGateway() {
            @Override public CaptureSearchResponse search(Authority a, CaptureQuery q, Duration t) { throw unavailable("PLATE query adapter is not connected"); }
            @Override public CaptureDetail get(Authority a, UUID id, Duration t) { throw unavailable("PLATE detail adapter is not connected"); }
            @Override public GeoFeatureCollection map(Authority a, MapQuery q, Duration t) { throw unavailable("PLATE map adapter is not connected"); }
        });
    }
}
