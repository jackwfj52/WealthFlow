package org.jack.wealthflow.service;

/** API Key 的内部加密边界，明文仅用于调用模型，不进入响应或日志。 */
public interface SecretProtector {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
