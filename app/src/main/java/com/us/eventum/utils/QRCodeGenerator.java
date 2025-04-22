package com.us.eventum.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONException;
import org.json.JSONObject;

import com.us.eventum.models.Attendee;

public class QRCodeGenerator {
    
    /**
     * Genera un código QR para un asistente con sus datos
     * @param attendee El asistente cuyos datos se codificarán
     * @param size Tamaño del código QR en píxeles
     * @return Un Bitmap con el código QR generado
     */
    public static Bitmap generateQRCode(Attendee attendee, int size) {
        try {
            // Crear un objeto JSON con los datos del asistente
            JSONObject attendeeData = new JSONObject();
            attendeeData.put("id", attendee.getId());
            attendeeData.put("name", attendee.getName());
            attendeeData.put("lastName", attendee.getLastName());
            attendeeData.put("email", attendee.getEmail());
            attendeeData.put("phone", attendee.getPhone());
            attendeeData.put("eventId", attendee.getEventId());
            
            // Convertir el JSON a string
            String attendeeJson = attendeeData.toString();
            
            // Generar el código QR
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(attendeeJson, BarcodeFormat.QR_CODE, size, size);
            
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