package citi.equities.lifecycleqa.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

public class RSAUtil {
    private static final Logger log = LoggerFactory.getLogger(RSAUtil.class);
    private static final String RSA_PRIVATEKEY = "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEAsc";

    public static String decrypt(String str) {
        try {
            byte[] inputByte = Base64Util.convertStringToByteArray(str);
            byte[] decoded = Base64Util.convertStringToByteArray(RSA_PRIVATEKEY);

            RSAPrivateKey priKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));

            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, priKey);
            return new String(cipher.doFinal(inputByte));
        } catch (Exception e) {
            log.error("RSA decrypt error: {}", e.getMessage());
            return "";
        }
    }
}
