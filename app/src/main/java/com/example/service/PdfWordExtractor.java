package com.example.service;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfWordExtractor {

    public static final int MAX_PAGES = 20;
    private static final int MAX_BITMAP_DIMENSION = 1600;
    private static final int MAX_BITMAP_PIXELS = 2_000_000;
    private static final float MAX_RENDER_SCALE = 2.0f;
    private static final int JPEG_QUALITY = 75;

    private final ContentResolver contentResolver;
    private final GeminiHelper geminiHelper;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface Callback {
        void onProgress(int currentPage, int totalPages);
        void onSuccess(List<GeminiHelper.WordResult> results, int processedPages, boolean pageLimitApplied);
        void onError(String message);
    }

    public PdfWordExtractor(Context context, GeminiHelper geminiHelper) {
        this.contentResolver = context.getApplicationContext().getContentResolver();
        this.geminiHelper = geminiHelper;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void extract(Uri uri, Callback callback) {
        executor.execute(() -> extractInBackground(uri, callback));
    }

    private void extractInBackground(Uri uri, Callback callback) {
        Map<String, GeminiHelper.WordResult> uniqueResults = new LinkedHashMap<>();

        try (ParcelFileDescriptor fileDescriptor = contentResolver.openFileDescriptor(uri, "r")) {
            if (fileDescriptor == null) {
                postError(callback, "선택한 PDF 파일을 열 수 없습니다.");
                return;
            }

            try (PdfRenderer renderer = new PdfRenderer(fileDescriptor)) {
                int pageCount = renderer.getPageCount();
                if (pageCount == 0) {
                    postError(callback, "빈 PDF 문서입니다.");
                    return;
                }

                int pagesToProcess = Math.min(pageCount, MAX_PAGES);
                boolean pageLimitApplied = pageCount > MAX_PAGES;

                for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
                    postProgress(callback, pageIndex + 1, pagesToProcess);
                    String base64Image = renderPageToBase64(renderer, pageIndex);
                    List<GeminiHelper.WordResult> pageResults =
                            geminiHelper.extractWordsFromPdfImageBlocking(base64Image);

                    for (GeminiHelper.WordResult result : pageResults) {
                        if (result.word == null || result.word.trim().isEmpty()) {
                            continue;
                        }
                        String key = result.word.trim().toLowerCase(Locale.ROOT);
                        if (!uniqueResults.containsKey(key)) {
                            uniqueResults.put(key, result);
                        }
                    }
                }

                postSuccess(
                        callback,
                        new ArrayList<>(uniqueResults.values()),
                        pagesToProcess,
                        pageLimitApplied
                );
            }
        } catch (SecurityException e) {
            postError(callback, "암호로 보호되었거나 접근할 수 없는 PDF입니다.");
        } catch (IllegalArgumentException e) {
            postError(callback, "지원하지 않거나 손상된 PDF 형식입니다: " + getErrorMessage(e));
        } catch (Exception e) {
            postError(callback, "PDF 분석 실패: " + getErrorMessage(e));
        }
    }

    String renderPageToBase64(PdfRenderer renderer, int pageIndex) throws IOException {
        try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
            int sourceWidth = page.getWidth();
            int sourceHeight = page.getHeight();
            float dimensionScale = Math.min(
                    (float) MAX_BITMAP_DIMENSION / sourceWidth,
                    (float) MAX_BITMAP_DIMENSION / sourceHeight
            );
            float pixelScale = (float) Math.sqrt(
                    (double) MAX_BITMAP_PIXELS / ((double) sourceWidth * sourceHeight)
            );
            float scale = Math.min(MAX_RENDER_SCALE, Math.min(dimensionScale, pixelScale));
            scale = Math.max(scale, 0.1f);

            int width = Math.max(1, Math.round(sourceWidth * scale));
            int height = Math.max(1, Math.round(sourceHeight * scale));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            try {
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                        throw new IOException("PDF 페이지 이미지 압축에 실패했습니다.");
                    }
                    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
                }
            } finally {
                bitmap.recycle();
            }
        }
    }

    private String getErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private void postProgress(Callback callback, int currentPage, int totalPages) {
        mainHandler.post(() -> callback.onProgress(currentPage, totalPages));
    }

    private void postSuccess(
            Callback callback,
            List<GeminiHelper.WordResult> results,
            int processedPages,
            boolean pageLimitApplied
    ) {
        mainHandler.post(() -> callback.onSuccess(results, processedPages, pageLimitApplied));
    }

    private void postError(Callback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
