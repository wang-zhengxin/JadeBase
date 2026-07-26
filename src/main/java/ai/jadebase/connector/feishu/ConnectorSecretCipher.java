package ai.jadebase.connector.feishu;

import ai.jadebase.common.CredentialCipher;
import org.springframework.stereotype.Component;

@Component
public class ConnectorSecretCipher {

    private final CredentialCipher cipher;

    public ConnectorSecretCipher(CredentialCipher cipher) {
        this.cipher = cipher;
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("App Secret 不能为空");
        return cipher.encrypt(value);
    }

    public String decrypt(String value) {
        return cipher.decrypt(value);
    }
}
