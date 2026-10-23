package com.group1.bookit;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;

/** Produces images; authentication decides whether a login QR may be issued. */
public final class CodeGenerator {
    private CodeGenerator() {}

    public static BufferedImage generateBookBarcode(String bookCopyId) throws WriterException {
        if (bookCopyId == null || !bookCopyId.matches("[\\x21-\\x7E]{1,45}")) {
            throw new IllegalArgumentException("Use a copy ID of 1 to 45 printable ASCII characters.");
        }
        BitMatrix matrix = new MultiFormatWriter().encode(
                "BOOK:" + bookCopyId, BarcodeFormat.CODE_128, 600, 150);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    public static BufferedImage generateLoginQrCode(String rawToken) throws WriterException {
        if (!QrTokens.isValidToken(rawToken)) {
            throw new IllegalArgumentException("A generated login token is required.");
        }
        BitMatrix matrix = new MultiFormatWriter().encode(
                "LOGIN:" + rawToken, BarcodeFormat.QR_CODE, 360, 360,
                Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 4));
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    public static byte[] loginQrPng(String rawToken) throws WriterException, IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(generateLoginQrCode(rawToken), "PNG", output)) {
                throw new IOException("PNG encoding is unavailable.");
            }
            return output.toByteArray();
        }
    }

    /** Encodes the exact persisted barcode. Do not add the legacy BOOK: prefix. */
    public static BufferedImage generateCopyBarcode(String exactStoredBarcode) throws WriterException {
        if (exactStoredBarcode == null || !exactStoredBarcode.matches("[\\x21-\\x7E]{1,50}")) {
            throw new IllegalArgumentException("Use a stored barcode of 1 to 50 non-space printable ASCII characters.");
        }
        // ZXing's one-dimensional MARGIN is the TOTAL extra module count.
        // Twenty gives at least ten white modules on each side of CODE 128.
        BitMatrix matrix = new MultiFormatWriter().encode(exactStoredBarcode,
                BarcodeFormat.CODE_128, 1200, 240, Map.of(EncodeHintType.MARGIN, 20));
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    /** Barcode-only high-resolution PNG, including its left/right quiet zones. */
    public static byte[] copyBarcodePng(String exactStoredBarcode) throws WriterException, IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(generateCopyBarcode(exactStoredBarcode), "PNG", output)) {
                throw new IOException("PNG encoding is unavailable.");
            }
            return output.toByteArray();
        }
    }
}
