package com.us.eventum.core.utils;

import android.util.Base64;

import com.us.eventum.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Token firmado para check-in por QR: uid + eventId + timestamp + HMAC-SHA256.
 * Solo se admite la versión actual ({@link #VERSION}); no hay compatibilidad con formatos antiguos.
 */
public final class AttendeeQrToken {

    public static final int VERSION = 2;
    public static final long TOKEN_TTL_MS = 30L * 60L * 1000L;
    public static final long REFRESH_INTERVAL_MS = 5L * 60L * 1000L;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private AttendeeQrToken() {
    }

    public static String buildPayload(String uid, String eventId) {
        return buildPayload(uid, eventId, System.currentTimeMillis());
    }

    public static String buildPayload(String uid, String eventId, long timestampMs) {
        if (isEmpty(uid) || isEmpty(eventId) || timestampMs <= 0L) {
            return null;
        }
        String signature = sign(uid, eventId, timestampMs);
        if (signature.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject()
                    .put("v", VERSION)
                    .put("uid", uid)
                    .put("eventId", eventId)
                    .put("ts", timestampMs)
                    .put("sig", signature)
                    .toString();
        } catch (JSONException e) {
            return null;
        }
    }

    public static ParseResult parse(String rawContent, String expectedEventId) {
        if (isEmpty(rawContent) || isEmpty(expectedEventId)) {
            return ParseResult.invalid(QrCheckInResult.INVALID_TOKEN);
        }

        try {
            JSONObject json = new JSONObject(rawContent.trim());

            if (json.optInt("v", -1) != VERSION) {
                return ParseResult.invalid(QrCheckInResult.INVALID_TOKEN);
            }

            String uid = json.optString("uid", null);
            String eventId = json.optString("eventId", null);
            long ts = json.optLong("ts", 0L);
            String sig = json.optString("sig", null);

            if (isEmpty(uid) || isEmpty(eventId) || ts <= 0L || isEmpty(sig)) {
                return ParseResult.invalid(QrCheckInResult.INVALID_TOKEN);
            }

            if (!eventId.equals(expectedEventId)) {
                return ParseResult.of(QrCheckInResult.WRONG_EVENT, uid, eventId);
            }

            if (System.currentTimeMillis() - ts > TOKEN_TTL_MS) {
                return ParseResult.of(QrCheckInResult.EXPIRED_TOKEN, uid, eventId);
            }

            if (!sign(uid, eventId, ts).equals(sig)) {
                return ParseResult.invalid(QrCheckInResult.INVALID_TOKEN);
            }

            return ParseResult.ok(uid, eventId);
        } catch (JSONException e) {
            return ParseResult.invalid(QrCheckInResult.INVALID_TOKEN);
        }
    }

    private static String sign(String uid, String eventId, long timestampMs) {
        String canonical = VERSION + "|" + uid + "|" + eventId + "|" + timestampMs;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    BuildConfig.QR_SIGNING_SECRET.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return "";
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class ParseResult {
        private final QrCheckInResult result;
        private final String uid;
        private final String eventId;

        private ParseResult(QrCheckInResult result, String uid, String eventId) {
            this.result = result;
            this.uid = uid;
            this.eventId = eventId;
        }

        static ParseResult ok(String uid, String eventId) {
            return new ParseResult(QrCheckInResult.VALID, uid, eventId);
        }

        static ParseResult of(QrCheckInResult result, String uid, String eventId) {
            return new ParseResult(result, uid, eventId);
        }

        static ParseResult invalid(QrCheckInResult result) {
            return new ParseResult(result, null, null);
        }

        public boolean isValidForCheckIn() {
            return result == QrCheckInResult.VALID && uid != null && eventId != null;
        }

        public QrCheckInResult getResult() {
            return result;
        }

        public String getUid() {
            return uid;
        }

        public String getEventId() {
            return eventId;
        }
    }
}
