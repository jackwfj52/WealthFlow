package org.jack.wealthflow.service;

import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 使用真实 Windows DPAPI 验证加密往返。
 * 仅在 Windows 上运行；DPAPI 与当前用户账户绑定。
 */
@EnabledOnOs(OS.WINDOWS)
class WindowsDpapiSecretProtectorTest {

    private final WindowsDpapiSecretProtector protector =
            new WindowsDpapiSecretProtector();

    @Test
    void shouldRoundTripWithoutStoringPlaintext() {
        String plaintext = "sk-test-1234567890abcdef";

        String ciphertext = protector.encrypt(plaintext);

        assertNotEquals(plaintext, ciphertext);
        assertFalse(ciphertext.contains("sk-test"));
        assertEquals(plaintext, protector.decrypt(ciphertext));
    }

    @Test
    void shouldFailWithBusinessErrorOnInvalidCiphertext() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> protector.decrypt("bm90LWRwYXBpLWJsb2I=")
        );

        assertEquals(ErrorCode.DPAPI_DECRYPT_FAILED, exception.getErrorCode());
    }
}
