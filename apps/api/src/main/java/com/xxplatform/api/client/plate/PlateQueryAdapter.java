package com.xxplatform.api.client.plate;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.*;
import com.xxplatform.api.service.ingestion.DataApiException;
import com.xxplatform.api.client.ingestion.IngestionQueryAdapter;
import com.xxplatform.api.service.plate.PlateQueryService.*;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

/** Conversion into the documented PLATE query contract, without a new transport. */
public final class PlateQueryAdapter implements QueryGateway {
    private final Client client;
    private final TargetLookup targets;
    public PlateQueryAdapter(Client client, TargetLookup targets) {
        this.client = Objects.requireNonNull(client);
        this.targets = Objects.requireNonNull(targets);
    }
    public PreparedQuery prepare(Authority authority, CaptureQuery query, boolean paged) {
        if (!"PLATE".equals(query.dataType())) {
            throw new DataApiException(ErrorCode.REQUIREMENT_PENDING, "dataType",
                    "This source adapter supports PLATE only");
        }
        var filter = new LinkedHashMap<String,Object>();
        filter.put("dataType", "PLATE");
        filter.put("timeRange", Map.of("from", query.from().toInstant().toString(),
                "to", query.to().toInstant().toString()));
        if (query.siteIds() != null) filter.put("siteIds", query.siteIds());
        if (query.deviceIds() != null) filter.put("deviceIds", query.deviceIds());
        List<UUID> targetIds = query.targetIds();
        boolean noMatches = false;
        String rawPlate = query.exactFilters().get("plateNumber");
        if (rawPlate != null) {
            Optional<UUID> found = Objects.requireNonNull(targets.resolve(authority, rawPlate));
            noMatches = found.isEmpty() || (targetIds != null && !targetIds.contains(found.orElse(null)));
            targetIds = found.map(List::of).orElse(List.of());
        }
        if (targetIds != null && !targetIds.isEmpty()) filter.put("targetIds", targetIds);
        if (query.exactFilters().containsKey("plateColor")) {
            filter.put("plateColor", query.exactFilters().get("plateColor"));
        }
        filter.put("sortBy", query.sortBy()); filter.put("sortOrder", query.sortOrder());
        if (paged) filter.put("pageSize", query.pageSize());
        return new PreparedQuery(Map.copyOf(filter), noMatches);
    }
    @Override public CaptureSearchResponse search(Authority authority, CaptureQuery query, Duration timeout) {
        PreparedQuery plan = prepare(authority, query, true);
        if (plan.noMatches()) {
            if (query.cursor() != null) {
                throw new DataApiException(ErrorCode.DATA_CURSOR_INVALID, "cursor",
                        "The query no longer matches the original target scope");
            }
            return new CaptureSearchResponse(List.of(), null, query.pageSize());
        }
        // Pass cursor separately: QuerySpec.parse does not accept it as a filter.
        CaptureSearchResponse page = call(() -> client.search(
                authority, plan.filters(), query.cursor(), timeout));
        if (page == null || page.getItems() == null || page.getItems().size() > query.pageSize()
                || !Integer.valueOf(query.pageSize()).equals(page.getPageSize())) throw badResponse();
        for (CaptureRecord item : page.getItems()) {
            if (!(item instanceof PlateCaptureRecord row)) throw badResponse();
            validateRecord(row, authority);
        }
        return page;
    }
    @Override public CaptureDetail get(Authority authority, UUID captureId, Duration timeout) {
        CaptureDetail result = call(() -> client.get(authority, captureId, timeout));
        if (result == null) throw new DataApiException(ErrorCode.DATA_NOT_FOUND, null, "Capture not found");
        if (!(result.getRecord() instanceof PlateCaptureRecord row)
                || !captureId.equals(row.getCaptureId())) throw badResponse();
        validateRecord(row, authority);
        return result;
    }
    @Override public GeoFeatureCollection map(Authority authority, MapQuery query, Duration timeout) {
        // The full Context/frozen query is resolved by the trusted data client.
        // Neither current-page capture IDs nor Context.targets previews are used.
        GeoFeatureCollection result = call(() -> client.map(authority, query, timeout));
        if (result == null || result.getFeatures() == null
                || result.getFeatures().size() > query.limit()) throw badResponse();
        return result;
    }
    private static void validateRecord(PlateCaptureRecord row, Authority authority) {
        if (!"PLATE".equals(row.getDataType()) || row.getCaptureId() == null
                || row.getCaptureTime() == null || row.getIngestedAt() == null
                || row.getTargetType() != TargetType.PLATE
                || !Boolean.TRUE.equals(row.getIsMasked()) || row.getPlate() == null
                || row.getPlate().getPlateMasked() == null) throw badResponse();
        // Keep authorization tuples intact; ownOnly is also enforced in the data query.
        boolean inScope = authority.grants().stream().anyMatch(grant ->
                "PLATE".equals(grant.dataType()) && grant.siteId().equals(row.getSiteId()));
        if (!inScope) throw badResponse();
        var lon = row.getLongitude(); var lat = row.getLatitude();
        if ((lon == null) != (lat == null)) throw badResponse();
        if (lon != null && (lon.abs().compareTo(java.math.BigDecimal.valueOf(180)) > 0
                || lat.abs().compareTo(java.math.BigDecimal.valueOf(90)) > 0)) throw badResponse();
    }
    private static <T> T call(Supplier<T> action) {
        try { return action.get(); }
        catch (IngestionQueryAdapter.Failure error) {
            throw IngestionQueryAdapter.mapFailure(error);
        }
    }
    private static DataApiException badResponse() {
        return new DataApiException(ErrorCode.SYSTEM_UNAVAILABLE, null,
                "PLATE data adapter returned an invalid result");
    }
    public record PreparedQuery(Map<String,Object> filters, boolean noMatches) {
        public PreparedQuery { filters = Map.copyOf(filters); }
        @Override public String toString() { return "PreparedQuery[redacted]"; }
    }
    /** Calls the approved target LOOKUP service; never guesses or registers identities. */
    @FunctionalInterface public interface TargetLookup {
        Optional<UUID> resolve(Authority authority, String rawPlate);
    }
    /**
     * A Java consumption seam for DEV-04, not an implemented HTTP/RPC service.
     * Preserve original query sessions and cursor/scope/condition bindings;
     * enforce budgets, full tuple scope including ownOnly, masking and snapshots.
     * Map resolves full Context plus filters and returns WGS84 [lon,lat] features.
     * Do not flatten tuple grants into a Cartesian product or truncate whole results.
     */
    public interface Client {
        CaptureSearchResponse search(Authority authority, Map<String,Object> filters,
                                     String cursor, Duration timeout);
        CaptureDetail get(Authority authority, UUID captureId, Duration timeout);
        GeoFeatureCollection map(Authority authority, MapQuery query, Duration timeout);
    }
}
