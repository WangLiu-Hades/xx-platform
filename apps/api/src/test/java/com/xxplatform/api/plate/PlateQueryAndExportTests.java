package com.xxplatform.api.plate;

import com.xxplatform.api.client.plate.PlateQueryAdapter;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.generated.model.CaptureSearchResponse;
import com.xxplatform.api.service.ingestion.DataApiException;
import com.xxplatform.api.service.plate.*;
import com.xxplatform.api.service.plate.PlateQueryService.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PlateQueryAndExportTests {
    private static UUID id(long n) { return new UUID(0,n); }
    private final OffsetDateTime now=OffsetDateTime.parse("2026-10-24T12:00:00Z");
    private final Authority authority=new Authority(id(99),"scope1",
            List.of(new ScopeTuple("PLATE",id(1),true),new ScopeTuple("FACE",id(2),false)),
            new QueryBudget(Duration.ofDays(31),Duration.ofSeconds(5)));
    private final PlateQueryAdapter.Client client=mock(PlateQueryAdapter.Client.class);
    private final PlateQueryAdapter.TargetLookup targets=mock(PlateQueryAdapter.TargetLookup.class);
    private final PlateQueryAdapter adapter=new PlateQueryAdapter(client,targets);
    private CaptureQuery query(List<UUID> ids, Map<String,String> exact, String cursor) {
        return new CaptureQuery("PLATE",now.minusHours(1),now,List.of(id(1)),ids,List.of(id(10)),
                cursor,20,"captureTime","DESC",exact);
    }
    private void fails(ErrorCode code, Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(DataApiException.class,
                error->assertThat(error.code()).isEqualTo(code));
    }
    @Test void rawPlateIsResolvedAndNeverSentAsAWorkerFilter() {
        when(targets.resolve(authority,"SYNTHETIC-PLATE")).thenReturn(Optional.of(id(8)));
        var plan=adapter.prepare(authority,query(null,Map.of("plateNumber","SYNTHETIC-PLATE","plateColor","BLUE"),null),true);
        assertThat(plan.filters()).containsEntry("targetIds",List.of(id(8)))
                .containsEntry("plateColor","BLUE").doesNotContainKeys("plateNumber","cursor");
        assertThat(plan.noMatches()).isFalse();
    }
    @Test void explicitTargetFilterAndRawIdentifierAreIntersected() {
        when(targets.resolve(authority,"SYNTHETIC-PLATE")).thenReturn(Optional.of(id(8)));
        var plan=adapter.prepare(authority,query(List.of(id(9)),Map.of("plateNumber","SYNTHETIC-PLATE"),null),true);
        assertThat(plan.noMatches()).isTrue();
    }
    @Test void unresolvedIdentifierReturnsEmptyAndNeverBecomesAnAllDataQuery() {
        when(targets.resolve(authority,"UNKNOWN")).thenReturn(Optional.empty());
        CaptureSearchResponse result=adapter.search(authority,
                query(null,Map.of("plateNumber","UNKNOWN"),null),Duration.ofSeconds(5));
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getNextCursor()).isNull();
        verifyNoInteractions(client);
    }
    @Test void unresolvedIdentifierCannotContinueAnOldCursor() {
        when(targets.resolve(authority,"UNKNOWN")).thenReturn(Optional.empty());
        fails(ErrorCode.DATA_CURSOR_INVALID,()->adapter.search(authority,
                query(null,Map.of("plateNumber","UNKNOWN"),"old-cursor"),Duration.ofSeconds(5)));
        verifyNoInteractions(client);
    }
    @Test void cursorAndTupleScopeRemainSeparateAndUnchanged() {
        when(client.search(same(authority),anyMap(),eq("opaque"),any()))
                .thenReturn(new CaptureSearchResponse(List.of(),null,20));
        adapter.search(authority,query(null,Map.of(),"opaque"),Duration.ofSeconds(5));
        verify(client).search(same(authority),argThat(filters ->
                !filters.containsKey("cursor") && filters.get("pageSize").equals(20)),
                eq("opaque"),eq(Duration.ofSeconds(5)));
    }
    private CaptureExportSourceAdapter export(OffsetDateTime expires, String kind) {
        return new CaptureExportSourceAdapter((permission,csrf)-> {
            assertThat(permission).isEqualTo("capture:export"); return authority;
        },(context,current)->new CaptureExportSourceAdapter.FrozenContext(
                context,kind,expires,query(null,Map.of(),"old-page-cursor")),adapter,
                Clock.fixed(now.toInstant(),ZoneOffset.UTC));
    }
    @Test void exportUsesTheWholeFrozenQueryWithoutPageOrCursor() {
        var source=export(now.plusMinutes(10),"CAPTURE_SEARCH").prepare(
                "csrf",id(20),"XLSX",List.of("captureId","plateMasked"),"Synthetic purpose");
        assertThat(source.query().filters()).containsKeys("timeRange","siteIds","deviceIds")
                .doesNotContainKeys("cursor","pageSize","page","items");
        assertThat(source.authority()).isSameAs(authority);
        assertThat(source.authority().grants()).hasSize(2);
        verifyNoInteractions(client);
    }
    @Test void expiredContextCannotProduceAnExportSource() {
        fails(ErrorCode.CONTEXT_EXPIRED,()->export(now,"CAPTURE_SEARCH")
                .prepare("csrf",id(20),"CSV",null,"Synthetic purpose"));
    }
    @Test void wrongContextTypeCannotProduceAnExportSource() {
        fails(ErrorCode.DATA_INVALID_FILTER,()->export(now.plusMinutes(1),"DATASET")
                .prepare("csrf",id(20),"CSV",null,"Synthetic purpose"));
    }
    @Test void emptyColumnsAreRejected() {
        fails(ErrorCode.VALIDATION_ERROR,()->export(now.plusMinutes(1),"CAPTURE_SEARCH")
                .prepare("csrf",id(20),"CSV",List.of(),"Synthetic purpose"));
    }
    @Test void plaintextOrUnknownColumnsAreRejected() {
        fails(ErrorCode.VALIDATION_ERROR,()->export(now.plusMinutes(1),"CAPTURE_SEARCH")
                .prepare("csrf",id(20),"CSV",List.of("plateNumber"),"Synthetic purpose"));
    }
}
