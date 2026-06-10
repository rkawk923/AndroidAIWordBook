package com.example.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class PdfWordExtractorInstrumentedTest {

    @Test
    public void rendersTextAndGraphicPagesAsJpeg() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File pdfFile = new File(context.getCacheDir(), "pdf-render-test.pdf");
        createTestPdf(pdfFile);

        PdfWordExtractor extractor = new PdfWordExtractor(context, new GeminiHelper());
        try (ParcelFileDescriptor descriptor =
                     ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            assertEquals(2, renderer.getPageCount());

            for (int pageIndex = 0; pageIndex < renderer.getPageCount(); pageIndex++) {
                String base64 = extractor.renderPageToBase64(renderer, pageIndex);
                assertNotNull(base64);
                assertTrue(base64.length() > 100);

                byte[] jpeg = Base64.decode(base64, Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                assertNotNull(bitmap);
                assertTrue(bitmap.getWidth() <= 1600);
                assertTrue(bitmap.getHeight() <= 1600);
                bitmap.recycle();
            }
        } finally {
            extractor.shutdown();
            pdfFile.delete();
        }
    }

    private void createTestPdf(File target) throws Exception {
        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.PageInfo firstPageInfo =
                    new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page firstPage = document.startPage(firstPageInfo);
            Canvas firstCanvas = firstPage.getCanvas();
            firstCanvas.drawColor(Color.WHITE);
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(32f);
            firstCanvas.drawText("Vocabulary: apple - 사과", 40f, 100f, textPaint);
            document.finishPage(firstPage);

            PdfDocument.PageInfo secondPageInfo =
                    new PdfDocument.PageInfo.Builder(1200, 800, 2).create();
            PdfDocument.Page secondPage = document.startPage(secondPageInfo);
            Canvas secondCanvas = secondPage.getCanvas();
            secondCanvas.drawColor(Color.WHITE);
            Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shapePaint.setColor(Color.BLUE);
            secondCanvas.drawRect(100f, 100f, 1100f, 700f, shapePaint);
            document.finishPage(secondPage);

            try (FileOutputStream output = new FileOutputStream(target)) {
                document.writeTo(output);
            }
        } finally {
            document.close();
        }
    }
}
