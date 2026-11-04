package schultz.thomas.schub.games.business.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Le laissez-passer d'un salon d'équipe (Schub#171), signé par le BFF : <charge>.<signature> en base64url, la charge
// {"team":"…","exp":…}. Même dérivation de clé que RelayPassSigner (schub-back-bff). Sans secret, aucun salon
// d'équipe ne s'ouvre.
public class TeamPassVerifier {

    private static final String USAGE = "premadelab-relay-pass";
    private static final Pattern CHARGE = Pattern.compile("\\{\"team\":\"([0-9a-f]{24})\",\"exp\":(\\d{1,12})}");

    private final byte[] cle;
    private final Clock clock;

    public TeamPassVerifier(String secret, Clock clock) {
        this.cle = secret == null || secret.isBlank() ? null
                : hmac(secret.getBytes(StandardCharsets.UTF_8), USAGE);
        this.clock = clock;
    }

    // room : <équipe>.<plan>. Le laissez-passer ouvre tous les salons de son équipe, et ceux-là seulement.
    public boolean permits(String pass, String room) {
        if (cle == null || pass == null || room == null) {
            return false;
        }
        int point = pass.indexOf('.');
        if (point <= 0) {
            return false;
        }
        String chargeEncodee = pass.substring(0, point);
        byte[] signature;
        String charge;
        try {
            signature = Base64.getUrlDecoder().decode(pass.substring(point + 1));
            charge = new String(Base64.getUrlDecoder().decode(chargeEncodee), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException illisible) {
            return false;
        }
        if (!MessageDigest.isEqual(signature, hmac(cle, chargeEncodee))) {
            return false;
        }
        Matcher lu = CHARGE.matcher(charge);
        return lu.matches()
                && Long.parseLong(lu.group(2)) > clock.instant().getEpochSecond()
                && room.startsWith(lu.group(1) + ".");
    }

    private static byte[] hmac(byte[] cle, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException absent) {
            throw new IllegalStateException(absent);
        }
    }
}
