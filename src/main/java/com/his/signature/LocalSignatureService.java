package com.his.signature;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 开发环境签名实现：HMAC-SHA256。
 * 生产环境替换为通过 SignaturePort 接入的 CA 签名服务，不得直接使用本实现。
 */
@Service
public class LocalSignatureService implements SignaturePort {

    private static final String SIGN_TYPE = "HMAC-SHA256-LOCAL";
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final String devKey;
    private final String certSn;

    public LocalSignatureService(@Value("${his.signature.dev-key}") String devKey,
                                 @Value("${his.signature.cert-sn}") String certSn) {
        this.devKey = devKey;
        this.certSn = certSn;
    }

    @Override
    public SignatureResult sign(SignCommand command) {
        String signature = hmac(canonical(command.docId(), command.versionNo(),
                command.signerId(), formatTime(command.signedAt()), command.contentJson()));
        return new SignatureResult(
                sha256(command.contentJson()),
                signature,
                certSn,
                SIGN_TYPE,
                command.signedAt());
    }

    @Override
    public boolean verify(VerifyCommand command) {
        String expected = hmac(canonical(command.docId(), command.versionNo(),
                command.signerId(), formatTime(command.signedAt()), command.contentJson()));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                command.signature().getBytes(StandardCharsets.UTF_8));
    }

    private String canonical(Long docId, Integer versionNo, Long signerId,
                             String signedAt, String contentJson) {
        return docId + "|" + versionNo + "|" + signerId + "|" + signedAt + "|" + contentJson;
    }

    private String formatTime(java.time.LocalDateTime time) {
        return time == null ? "-" : time.format(TS_FORMAT);
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(devKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("signature failed", ex);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("hash failed", ex);
        }
    }
}
