package org.jack.wealthflow.service;

import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 容器使用 AES-256-GCM；32 字节主密钥通过只读 secret 文件提供，不自动生成或更换。 */
@Component
@ConditionalOnProperty(name = "wealthflow.security.secret-protection", havingValue = "aes-gcm")
public class AesGcmSecretProtector implements SecretProtector {
    private static final String PREFIX = "aesgcm:v1:";
    private static final byte[] AAD = "WealthFlow:api-key:v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_LENGTH = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretProtector(@Value("${wealthflow.security.master-key-file:}") String keyFile) {
        byte[] decoded = null;
        try {
            if (keyFile == null || keyFile.isBlank()) throw new IllegalArgumentException();
            Path file = Path.of(keyFile);
            if (Files.size(file) > 256) throw new IllegalArgumentException();
            decoded = Base64.getDecoder().decode(Files.readString(file, StandardCharsets.UTF_8).strip());
            if (decoded.length != 32) throw new IllegalArgumentException();
            key = new SecretKeySpec(decoded, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("无法加载 AES 主密钥，请配置包含 Base64 编码的32字节随机密钥文件；不要更换已有数据库对应的主密钥");
        } finally {
            if (decoded != null) Arrays.fill(decoded, (byte) 0);
        }
    }

    @Override public String encrypt(String plaintext) {
        try {
            if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException();
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SECRET_ENCRYPT_FAILED, ErrorCode.SECRET_ENCRYPT_FAILED.getMessage());
        }
    }

    @Override public String decrypt(String ciphertext) {
        try {
            if (ciphertext == null || !ciphertext.startsWith(PREFIX) || ciphertext.length() > 65536)
                throw new IllegalArgumentException();
            byte[] packed = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (packed.length <= IV_LENGTH + 16) throw new IllegalArgumentException();
            byte[] iv = Arrays.copyOfRange(packed, 0, IV_LENGTH);
            byte[] plaintext = cipher(Cipher.DECRYPT_MODE, iv).doFinal(packed, IV_LENGTH, packed.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SECRET_DECRYPT_FAILED, ErrorCode.SECRET_DECRYPT_FAILED.getMessage());
        }
    }

    private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        return cipher;
    }
}
