package com.group1.bookit;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.awt.image.BufferedImage;
import java.util.Map;

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
}
