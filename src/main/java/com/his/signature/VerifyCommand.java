package com.his.signature;

import java.time.LocalDateTime;

public record VerifyCommand(
        Long docId,
        Integer versionNo,
        String contentJson,
        Long signerId,
        LocalDateTime signedAt,
        String signature) {
}
