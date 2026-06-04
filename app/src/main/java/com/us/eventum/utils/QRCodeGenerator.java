package com.us.eventum.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.Map;

public final class QRCodeGenerator {

    private static final int MARGIN = 1;

    private QRCodeGenerator() {
    }

    /**
     * Genera el bitmap del QR de check-in (payload firmado v{@link AttendeeQrToken#VERSION}).
     */
    public static Bitmap generateCheckInQr(String uid, String eventId, int sizePx) {
        String payload = AttendeeQrToken.buildPayload(uid, eventId);
        if (payload == null) {
            return null;
        }
        return encode(payload, sizePx);
    }

    private static Bitmap encode(String content, int sizePx) {
        if (content == null || sizePx <= 0) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, MARGIN);

            BitMatrix matrix = new QRCodeWriter().encode(
                    content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (WriterException e) {
            return null;
        }
    }
}
