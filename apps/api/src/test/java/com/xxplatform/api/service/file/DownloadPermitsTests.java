package com.xxplatform.api.service.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.FileProperties;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.CurrentDataScope;
import java.time.*;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DownloadPermitsTests {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private final UUID user = UUID.randomUUID();
    private final UUID file = UUID.randomUUID();
    private FileProperties properties;
    private CurrentDataScope live;
    private AuthenticatedSession session;

    @BeforeEach void setup() {
        properties = new FileProperties();
        properties.setDownloadPermitKey(Base64.getEncoder().encodeToString(new byte[32]));
        live = mock(CurrentDataScope.class);
        when(live.stamp(user)).thenReturn("current-scope-v1");
        session = new AuthenticatedSession(user, UUID.randomUUID(), utc(NOW.plusSeconds(900)), utc(NOW.plusSeconds(7200)), null);
    }
    private static OffsetDateTime utc(Instant t) { return t.atOffset(ZoneOffset.UTC); }
    private DownloadPermits at(long seconds) { return new DownloadPermits(properties, live, Clock.fixed(NOW.plusSeconds(seconds), ZoneOffset.UTC)); }
    private String issue() { return at(0).issue(session, file, utc(NOW.plusSeconds(10000))); }

    @Test void permitExpiresExactlyAtFiveMinutes() {
        String token = issue();
        assertDoesNotThrow(() -> at(299).require(session, file, token));
        assertEquals(ErrorCode.PERMISSION_DENIED, assertThrows(ApiException.class, () -> at(300).require(session, file, token)).code());
    }
    @Test void permitIsBoundToFileAndSession() {
        String token = issue();
        assertThrows(ApiException.class, () -> at(0).require(session, UUID.randomUUID(), token));
        AuthenticatedSession another = new AuthenticatedSession(user, UUID.randomUUID(), session.idleExpiresAt(), session.absoluteExpiresAt(), null);
        assertThrows(ApiException.class, () -> at(0).require(another, file, token));
    }
    @Test void revocationInvalidatesAnUnexpiredPermitImmediately() {
        String token = issue();
        when(live.stamp(user)).thenReturn("current-scope-v2");
        assertThrows(ApiException.class, () -> at(1).require(session, file, token));
    }
    @Test void missingOrModifiedPermitIsRejected() {
        assertThrows(ApiException.class, () -> at(0).require(session, file, null));
        String token = issue();
        assertThrows(ApiException.class, () -> at(0).require(session, file, token + "x"));
    }
    @Test void permitCannotOutliveFileRetention() {
        String token = at(0).issue(session, file, utc(NOW.plusSeconds(60)));
        assertDoesNotThrow(() -> at(59).require(session, file, token));
        assertThrows(ApiException.class, () -> at(60).require(session, file, token));
    }
    @Test void deploymentMustBindASigningKey() {
        properties.setDownloadPermitKey("");
        assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, assertThrows(ApiException.class, this::issue).code());
    }
}
