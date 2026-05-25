package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.common.exception.UnsupportedMediaTypeException;

import java.util.Set;

final class MimeValidator {

    static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "application/pdf", "text/plain");

    private MimeValidator() {}

    static void validate(byte[] bytes, String claimedType) {
        if (!ALLOWED_TYPES.contains(claimedType)) {
            throw new UnsupportedMediaTypeException(
                    "Content type '" + claimedType + "' is not allowed. Allowed: " + ALLOWED_TYPES);
        }
        // text/plain has no reliable magic bytes — whitelist check is sufficient
        if ("image/png".equals(claimedType) && !isPng(bytes)) {
            throw new UnsupportedMediaTypeException(
                    "File content does not match declared type image/png");
        }
        if ("image/jpeg".equals(claimedType) && !isJpeg(bytes)) {
            throw new UnsupportedMediaTypeException(
                    "File content does not match declared type image/jpeg");
        }
        if ("application/pdf".equals(claimedType) && !isPdf(bytes)) {
            throw new UnsupportedMediaTypeException(
                    "File content does not match declared type application/pdf");
        }
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A
                && (b[6] & 0xFF) == 0x1A && b[7] == 0x0A;
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3
                && (b[0] & 0xFF) == 0xFF
                && (b[1] & 0xFF) == 0xD8
                && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPdf(byte[] b) {
        return b.length >= 4
                && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }
}
