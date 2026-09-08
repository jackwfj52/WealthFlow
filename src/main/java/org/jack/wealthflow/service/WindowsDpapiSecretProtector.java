package org.jack.wealthflow.service;

import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import org.jack.wealthflow.constant.MessageConstant;
import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 基于 Windows DPAPI（CryptProtectData / CryptUnprotectData）的密钥保护器。
 *
 * <p>密钥与当前 Windows 用户账户绑定，密文以 Base64 文本存入数据库。
 * DPAPI 不可用或加解密失败时抛出明确业务错误，绝不回退为明文保存。
 * 本类不记录任何日志，明文与密文均不会进入日志或异常消息。</p>
 */
@Component
public class WindowsDpapiSecretProtector {

    public String encrypt(String plaintext) {
        try {
            byte[] protectedData = Crypt32Util.cryptProtectData(
                    plaintext.getBytes(StandardCharsets.UTF_8),
                    WinCrypt.CRYPTPROTECT_UI_FORBIDDEN
            );
            return Base64.getEncoder().encodeToString(protectedData);
        } catch (LinkageError e) {
            throw new BusinessException(
                    ErrorCode.DPAPI_UNAVAILABLE,
                    MessageConstant.DPAPI_UNAVAILABLE
            );
        } catch (RuntimeException e) {
            throw new BusinessException(
                    ErrorCode.DPAPI_ENCRYPT_FAILED,
                    MessageConstant.DPAPI_ENCRYPT_FAILED
            );
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] protectedData = Base64.getDecoder().decode(ciphertext);
            byte[] plaintext = Crypt32Util.cryptUnprotectData(
                    protectedData,
                    WinCrypt.CRYPTPROTECT_UI_FORBIDDEN
            );
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (LinkageError e) {
            throw new BusinessException(
                    ErrorCode.DPAPI_UNAVAILABLE,
                    MessageConstant.DPAPI_UNAVAILABLE
            );
        } catch (RuntimeException e) {
            throw new BusinessException(
                    ErrorCode.DPAPI_DECRYPT_FAILED,
                    MessageConstant.DPAPI_DECRYPT_FAILED
            );
        }
    }
}
