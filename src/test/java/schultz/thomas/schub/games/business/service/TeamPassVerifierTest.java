package schultz.thomas.schub.games.business.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

public class TeamPassVerifierTest {

    public static final String EQUIPE = "0123456789abcdef01234567";
    public static final String PLAN = "76543210fedcba9876543210";
    private static final Instant MAINTENANT = Instant.parse("2026-10-29T20:00:00Z");

    private final TeamPassVerifier verifier = new TeamPassVerifier("secret-de-test",
            Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    @Test
    @DisplayName("un laissez-passer signé par le BFF ouvre les salons de son équipe")
    void ouvreLesSalonsDeLEquipe() {
        String pass = signe("secret-de-test", EQUIPE, MAINTENANT.plusSeconds(600));

        assertThat(verifier.permits(pass, EQUIPE + "." + PLAN)).isTrue();
        assertThat(verifier.permits(pass, "fedcba9876543210fedcba98." + PLAN)).isFalse();
    }

    @Test
    @DisplayName("expiré, signé avec un autre secret ou retouché, il n'ouvre rien")
    void refuse() {
        String salon = EQUIPE + "." + PLAN;
        assertThat(verifier.permits(signe("secret-de-test", EQUIPE, MAINTENANT.minusSeconds(1)), salon)).isFalse();
        assertThat(verifier.permits(signe("autre-secret", EQUIPE, MAINTENANT.plusSeconds(600)), salon)).isFalse();
        String pass = signe("secret-de-test", EQUIPE, MAINTENANT.plusSeconds(600));
        assertThat(verifier.permits("x" + pass, salon)).isFalse();
        assertThat(verifier.permits("pas-un-laissez-passer", salon)).isFalse();
    }

    @Test
    @DisplayName("sans secret configuré, aucun salon d'équipe ne s'ouvre")
    void sansSecret() {
        TeamPassVerifier sansSecret = new TeamPassVerifier("", Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        assertThat(sansSecret.permits(signe("", EQUIPE, MAINTENANT.plusSeconds(600)), EQUIPE + "." + PLAN)).isFalse();
    }

    // Ce que fait RelayPassSigner (schub-back-bff).
    public static String signe(String secret, String equipe, Instant expire) {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String charge = base64.encodeToString(("{\"team\":\"" + equipe + "\",\"exp\":" + expire.getEpochSecond() + "}")
                .getBytes(StandardCharsets.UTF_8));
        byte[] cle = hmac(secret.getBytes(StandardCharsets.UTF_8), "premadelab-relay-pass");
        return charge + "." + base64.encodeToString(hmac(cle, charge));
    }

    private static byte[] hmac(byte[] cle, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle.length == 0 ? new byte[1] : cle, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
