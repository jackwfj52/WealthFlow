package org.jack.wealthflow.service;

import org.jack.wealthflow.exception.BusinessException;
import org.jack.wealthflow.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class AesGcmSecretProtectorTest {
    @TempDir Path temp;

    Path key(String name) throws Exception {
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        return Files.writeString(temp.resolve(name), Base64.getEncoder().encodeToString(random));
    }

    @Test void restartWithSameKeyDecryptsAndEveryEncryptionUsesFreshNonce() throws Exception {
        Path file = key("master");
        var first = new AesGcmSecretProtector(file.toString());
        String encrypted = first.encrypt("fixture-api-key-123456");
        assertFalse(encrypted.contains("fixture-api-key"));
        assertNotEquals(encrypted, first.encrypt("fixture-api-key-123456"));
        assertEquals("fixture-api-key-123456", new AesGcmSecretProtector(file.toString()).decrypt(encrypted));
    }

    @Test void tamperingWrongKeyAndLegacyCiphertextCannotFallBackToPlaintext() throws Exception {
        var source = new AesGcmSecretProtector(key("source").toString());
        var wrong = new AesGcmSecretProtector(key("wrong").toString());
        String encrypted = source.encrypt("fixture-api-key-123456");
        var error = assertThrows(BusinessException.class, () -> wrong.decrypt(encrypted));
        assertEquals(ErrorCode.SECRET_DECRYPT_FAILED, error.getErrorCode());
        assertFalse(error.getMessage().contains("fixture-api-key"));
        byte[] packed = Base64.getDecoder().decode(encrypted.substring("aesgcm:v1:".length()));
        packed[packed.length - 1] ^= 1;
        assertThrows(BusinessException.class, () -> source.decrypt("aesgcm:v1:" + Base64.getEncoder().encodeToString(packed)));
        for (String invalid : new String[]{"legacy-dpapi", "aesgcm:v2:abcd", "aesgcm:v1:!", ""})
            assertThrows(BusinessException.class, () -> source.decrypt(invalid));
    }

    @Test void invalidOrAbsentMasterKeyFailsBeforeAcceptingRequests() throws Exception {
        assertThrows(IllegalStateException.class, () -> new AesGcmSecretProtector(""));
        assertThrows(IllegalStateException.class, () -> new AesGcmSecretProtector(temp.resolve("absent").toString()));
        Path invalid = Files.writeString(temp.resolve("invalid"), "not-a-valid-key");
        assertThrows(IllegalStateException.class, () -> new AesGcmSecretProtector(invalid.toString()));
        Files.writeString(invalid, Base64.getEncoder().encodeToString(new byte[16]));
        assertThrows(IllegalStateException.class, () -> new AesGcmSecretProtector(invalid.toString()));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({WindowsDpapiSecretProtector.class, AesGcmSecretProtector.class})
    static class Selection { }

    @Test void configurationSelectsExactlyOneProtectorAndPreservesWindowsDefault() throws Exception {
        var runner = new ApplicationContextRunner().withUserConfiguration(Selection.class);
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(SecretProtector.class).size());
            assertInstanceOf(WindowsDpapiSecretProtector.class, context.getBean(SecretProtector.class));
        });
        runner.withPropertyValues("wealthflow.security.secret-protection=aes-gcm",
                "wealthflow.security.master-key-file=" + key("context")).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(SecretProtector.class).size());
            assertInstanceOf(AesGcmSecretProtector.class, context.getBean(SecretProtector.class));
        });
    }
}
