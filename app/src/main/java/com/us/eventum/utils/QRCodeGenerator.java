package com.us.eventum.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONException;
import org.json.JSONObject;

import com.us.eventum.data.models.Attendee;

public class QRCodeGenerator {
    
    /**
     * Genera un código QR para verificar la asistencia de un usuario a un evento
     * @param attendeeId ID del asistente a verificar
     * @param eventId ID del evento al que pertenece la verificación
     * @param size Tamaño del código QR en píxeles
     * @return Un Bitmap con el código QR generado
     */
    public static Bitmap generateQRCode(String attendeeId, String eventId, int size) {
        try {
            // Crear un objeto JSON con los datos necesarios para la verificación
            JSONObject qrData = new JSONObject();
            qrData.put("attendeeId", attendeeId);
            qrData.put("eventId", eventId);
            
            // Convertir el JSON a string
            String qrContent = qrData.toString();
            
            // Generar el código QR
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, size, size);
            
            // Convertir la matriz de bits a un bitmap
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            
            return bitmap;
        } catch (WriterException | JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
} 