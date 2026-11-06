package com.his.signature;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSignatureServiceTest {

    private final LocalSignatureService service =
            new LocalSignatureService("test-sign-key-0123456789", "DEV-CA-TEST");

    @Test
    void signThenVerifyShouldPass() {
        LocalDateTime signedAt = LocalDateTime.of(2026, 11, 6, 10, 30, 0, 123_000_000);
        SignCommand command = new SignCommand(100L, 1, "{\"sections\":[]}", 7L, "张医生", signedAt);
        SignatureResult result = service.sign(command);

        VerifyCommand verify = new VerifyCommand(100L, 1, "{\"sections\":[]}", 7L,
                result.signedAt(), result.signature());

        assertTrue(service.verify(verify));
    }

    @Test
    void verifyShouldFailWhenContentTampered() {
        LocalDateTime signedAt = LocalDateTime.of(2026, 11, 6, 10, 30, 0, 123_000_000);
        SignCommand command = new SignCommand(100L, 1, "{\"sections\":[]}", 7L, "张医生", signedAt);
        SignatureResult result = service.sign(command);

        VerifyCommand tampered = new VerifyCommand(100L, 1,
                "{\"sections\":[{\"code\":\"x\",\"value\":\"篡改\"}]}", 7L,
                result.signedAt(), result.signature());

        assertFalse(service.verify(tampered));
    }

    @Test
    void verifyShouldFailWhenSignatureTampered() {
        LocalDateTime signedAt = LocalDateTime.of(2026, 11, 6, 10, 30, 0, 123_000_000);
        SignCommand command = new SignCommand(100L, 1, "{\"sections\":[]}", 7L, "张医生", signedAt);
        SignatureResult result = service.sign(command);

        VerifyCommand verify = new VerifyCommand(100L, 1, "{\"sections\":[]}", 7L,
                result.signedAt(), "00" + result.signature().substring(2));

        assertFalse(service.verify(verify));
    }
}
