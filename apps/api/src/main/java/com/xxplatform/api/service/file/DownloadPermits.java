package com.xxplatform.api.service.file;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.FileProperties;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.CurrentDataScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Five-minute, session/file/current-authorization-bound permits, shared across API instances. */
@Component
public class DownloadPermits {
    public static final String COOKIE_NAME = "FILEDL";
    private final FileProperties properties;
    private final CurrentDataScope live;
    private final java.time.Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DownloadPermits(FileProperties properties, CurrentDataScope live) { this(properties, live, java.time.Clock.systemUTC()); }
    DownloadPermits(FileProperties properties, CurrentDataScope live, java.time.Clock clock) {
        this.properties = properties; this.live = live; this.clock = clock;
    }

    public String issue(AuthenticatedSession session, UUID fileId, OffsetDateTime fileExpiresAt) {
        long until = Math.min(clock.instant().plusSeconds(300).getEpochSecond(),
                Math.min(fileExpiresAt.toEpochSecond(), session.absoluteExpiresAt().toEpochSecond()));
        return until + "." + signature(session, fileId, until);
    }

    public void require(AuthenticatedSession session, UUID fileId, String permit) {
        signingKey();
        try {
            if (permit == null) throw new IllegalArgumentException();
            String[] parts = permit.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            long until = Long.parseLong(parts[0]);
            long now = clock.instant().getEpochSecond();
            if (until <= now || until > now + 300) throw new IllegalArgumentException();
            if (!MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.US_ASCII),
                    signature(session, fileId, until).getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "下载许可缺失、已过期或授权已变化，请重新获取文件元数据");
        }
    }

    public boolean available() {
        try { signingKey(); return true; }
        catch (ApiException unavailable) { return false; }
    }

    private byte[] signingKey() {
        byte[] key;
        try { key = Base64.getDecoder().decode(properties.getDownloadPermitKey()); }
        catch (IllegalArgumentException e) { key = new byte[0]; }
        if (key.length < 32) throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "尚未绑定下载许可签名密钥");
        return key;
    }

    private String signature(AuthenticatedSession session, UUID fileId, long until) {
        byte[] key = signingKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String identity = "file-download-v1\n" + session.userId() + "\n" + session.sessionId()
                    + "\n" + fileId + "\n" + until + "\n" + live.stamp(session.userId());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Download signing failed", e); }
    }
}
