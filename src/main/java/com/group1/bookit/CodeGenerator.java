/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.group1.bookit;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.MultiFormatWriter;

import java.awt.image.BufferedImage;

public final class CodeGenerator {

    private CodeGenerator() {
        // Prevents creating an object from this utility class.
    }

    public static BufferedImage generateBookBarcode(int bookCopyId)
            throws WriterException {

        String bookValue = "BOOK:" + bookCopyId;

        BitMatrix barcode = new MultiFormatWriter().encode(
                bookValue,
                BarcodeFormat.CODE_128,
                500,
                120
        );

        return MatrixToImageWriter.toBufferedImage(barcode);
    }

    public static BufferedImage generateLoginQrCode(String loginToken)
        throws WriterException {

            String qrValue = "LOGIN:" + loginToken;

            BitMatrix qrCode = new MultiFormatWriter().encode(
                    qrValue,
                    BarcodeFormat.QR_CODE,
                    300,
                    300
            );

            return MatrixToImageWriter.toBufferedImage(qrCode);
        }

    public static BufferedImage generateAdminQrCode(String adminToken) throws WriterException {

        String adminValue = "ADMIN:" + adminToken;

        BitMatrix qrCode = new MultiFormatWriter().encode(
                adminValue,
                BarcodeFormat.QR_CODE,
                300,
                300
        );

        return MatrixToImageWriter.toBufferedImage(qrCode);
    }
}
