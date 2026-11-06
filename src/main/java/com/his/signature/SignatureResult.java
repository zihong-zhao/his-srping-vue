package com.his.signature;

import java.time.LocalDateTime;

public record SignatureResult(
        String contentHash,
        String signature,
        String certSn,
        String signType,
        LocalDateTime signedAt) {
}
