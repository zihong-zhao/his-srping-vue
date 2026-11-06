package com.his.signature;

import java.time.LocalDateTime;

public record SignCommand(
        Long docId,
        Integer versionNo,
        String contentJson,
        Long signerId,
        String signerName,
        LocalDateTime signedAt) {
}
