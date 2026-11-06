package com.his.signature;

/**
 * 电子签名端口。
 * 当前实现为开发环境的 HMAC 签名，生产环境替换为医院 CA 签名服务即可，业务代码不感知。
 */
public interface SignaturePort {

    SignatureResult sign(SignCommand command);

    boolean verify(VerifyCommand command);
}
