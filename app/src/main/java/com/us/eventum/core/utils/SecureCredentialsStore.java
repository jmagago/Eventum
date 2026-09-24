package com.us.eventum.core.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Almacena email y contraseña de "Recordarme" cifrados con AES-GCM y clave en Android Keystore.
 */
public final class SecureCredentialsStore {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "eventum_remember_me";
    private static final String SECURE_PREFS = "EventumLoginSecure";
    private static final String LEGACY_PREFS = "EventumLogin";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_REMEMBER_ME = "rememberMe";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SharedPreferences securePrefs;

    private SecureCredentialsStore(@NonNull SharedPreferences securePrefs) {
        this.securePrefs = securePrefs;
    }

    @NonNull
    public static SecureCredentialsStore create(@NonNull Context context)
            throws GeneralSecurityException, IOException {
        getOrCreateSecretKey();
        SharedPreferences prefs = context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE);
        SecureCredentialsStore store = new SecureCredentialsStore(prefs);
        store.migrateFromLegacy(context);
        return store;
    }

    public boolean isRememberMe() {
        return securePrefs.getBoolean(KEY_REMEMBER_ME, false);
    }

    @NonNull
    public String getEmail() {
        return decryptValue(securePrefs.getString(KEY_EMAIL, null));
    }

    @NonNull
    public String getPassword() {
        return decryptValue(securePrefs.getString(KEY_PASSWORD, null));
    }

    public void saveCredentials(@NonNull String email, @NonNull String password)
            throws GeneralSecurityException, IOException {
        securePrefs.edit()
                .putString(KEY_EMAIL, encryptValue(email))
                .putString(KEY_PASSWORD, encryptValue(password))
                .putBoolean(KEY_REMEMBER_ME, true)
                .apply();
    }

    public void clearCredentials() {
        securePrefs.edit()
                .remove(KEY_EMAIL)
                .remove(KEY_PASSWORD)
                .remove(KEY_REMEMBER_ME)
                .apply();
    }

    private void migrateFromLegacy(@NonNull Context context) {
        SharedPreferences legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        if (!legacy.getBoolean(KEY_REMEMBER_ME, false)) {
            legacy.edit().clear().apply();
            return;
        }

        String email = legacy.getString(KEY_EMAIL, "");
        String password = legacy.getString(KEY_PASSWORD, "");
        if (email != null && !email.isEmpty()) {
            try {
                saveCredentials(email, password != null ? password : "");
            } catch (GeneralSecurityException | IOException ignored) {
                clearCredentials();
            }
        }
        legacy.edit().clear().apply();
    }

    @NonNull
    private static SecretKey getOrCreateSecretKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            keyGenerator.init(spec);
            keyGenerator.generateKey();
        }
        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (key == null) {
            throw new GeneralSecurityException("No se pudo obtener la clave de cifrado");
        }
        return key;
    }

    @NonNull
    private static String encryptValue(@NonNull String plainText)
            throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    @NonNull
    private static String decryptValue(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            byte[] combined = Base64.decode(encoded, Base64.NO_WRAP);
            if (combined.length <= GCM_IV_LENGTH) {
                return "";
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ignored) {
            return "";
        }
    }
}
