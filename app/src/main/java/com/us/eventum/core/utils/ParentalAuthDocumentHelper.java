package com.us.eventum.core.utils;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.us.eventum.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Abrir o guardar documentos de autorización parental desde su URL de Storage.
 */
public final class ParentalAuthDocumentHelper {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ParentalAuthDocumentHelper() {
    }

    public static void saveToDownloads(@NonNull Activity activity,
                                       @NonNull String downloadUrl,
                                       @NonNull String suggestedFileName) {
        try {
            DownloadManager downloadManager =
                    (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                ToastUtils.showCustomToast(activity,
                        activity.getString(R.string.parental_auth_save_error),
                        ToastUtils.ToastType.ERROR);
                return;
            }
            String fileName = sanitizeFileName(suggestedFileName);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle(fileName);
            request.setDescription(activity.getString(R.string.parental_auth_saving));
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            downloadManager.enqueue(request);
            ToastUtils.showCustomToast(activity,
                    activity.getString(R.string.parental_auth_save_started),
                    ToastUtils.ToastType.SUCCESS);
        } catch (Exception e) {
            ToastUtils.showCustomToast(activity,
                    activity.getString(R.string.parental_auth_save_error),
                    ToastUtils.ToastType.ERROR);
        }
    }

    public static void viewWithSystemViewer(@NonNull Activity activity, @NonNull String downloadUrl) {
        ToastUtils.showCustomToast(activity,
                activity.getString(R.string.parental_auth_opening),
                ToastUtils.ToastType.INFO);
        EXECUTOR.execute(() -> {
            File cacheFile = null;
            try {
                String extension = resolveExtension(downloadUrl);
                String mimeType = mimeForExtension(extension);
                cacheFile = new File(activity.getCacheDir(),
                        "parental_auth_preview" + extension);
                downloadToFile(downloadUrl, cacheFile);
                File fileToOpen = cacheFile;
                MAIN.post(() -> openLocalFile(activity, fileToOpen, mimeType));
            } catch (Exception e) {
                if (cacheFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    cacheFile.delete();
                }
                MAIN.post(() -> ToastUtils.showCustomToast(activity,
                        activity.getString(R.string.parental_auth_view_error),
                        ToastUtils.ToastType.ERROR));
            }
        });
    }

    private static void openLocalFile(@NonNull Activity activity,
                                      @NonNull File file,
                                      @NonNull String mimeType) {
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(
                    intent, activity.getString(R.string.parental_auth_view)));
        } catch (Exception e) {
            ToastUtils.showCustomToast(activity,
                    activity.getString(R.string.parental_auth_view_error),
                    ToastUtils.ToastType.ERROR);
        }
    }

    private static void downloadToFile(@NonNull String downloadUrl, @NonNull File outFile)
            throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static String resolveExtension(@NonNull String url) {
        String lower = url.toLowerCase();
        int query = lower.indexOf('?');
        String path = query >= 0 ? lower.substring(0, query) : lower;
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (path.endsWith(".png")) {
            return ".png";
        }
        return ".pdf";
    }

    @NonNull
    private static String mimeForExtension(@NonNull String extension) {
        switch (extension) {
            case ".jpg":
                return "image/jpeg";
            case ".png":
                return "image/png";
            default:
                return "application/pdf";
        }
    }

    @NonNull
    private static String sanitizeFileName(@NonNull String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (cleaned.isEmpty()) {
            return "autorizacion_parental.pdf";
        }
        return cleaned;
    }
}
