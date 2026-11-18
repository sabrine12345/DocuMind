package com.documind.document;

import com.documind.common.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates uploaded files by extension AND magic bytes, so a renamed
 * executable can never be stored as a "pdf".
 */
@Component
public class FileValidator {

    private static final int HEADER_SIZE = 8;

    public DetectedFile validate(MultipartFile file, int maxSizeMb) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a non-empty file.");
        }
        long maxBytes = (long) maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("Maximum file size is " + maxSizeMb + " MB.");
        }
        byte[] header = readHeader(file);
        DetectedFile detected = detect(header);
        String original = safeName(file);
        String extension = extensionOf(original);
        if (!detected.accepts(extension)) {
            throw new BadRequestException("The file content of '" + original + "' does not match its extension.");
        }
        return new DetectedFile(original, detected.canonicalExtension(), detected.contentType());
    }

    public String safeName(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return "document";
        }
        String cleaned = org.springframework.util.StringUtils.cleanPath(original);
        int lastSlash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }
        if (cleaned.isBlank() || cleaned.contains("..")) {
            throw new BadRequestException("The file name is invalid.");
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[HEADER_SIZE];
        try (InputStream stream = file.getInputStream()) {
            int read = stream.read(header);
            if (read <= 0) {
                throw new BadRequestException("The file is empty.");
            }
            return header;
        } catch (IOException ex) {
            throw new BadRequestException("The uploaded file could not be read.");
        }
    }

    private DetectedFile detect(byte[] header) {
        if (header.length >= 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-') {
            return new DetectedFile(null, "pdf", "application/pdf", "pdf");
        }
        if (header.length >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
            return new DetectedFile(null, "png", "image/png", "png");
        }
        if (header.length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return new DetectedFile(null, "jpg", "image/jpeg", "jpg", "jpeg");
        }
        throw new BadRequestException("Only PDF, PNG and JPG documents are supported.");
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    public record DetectedFile(String originalName, String canonicalExtension, String contentType,
            String... acceptedExtensions) {
        boolean accepts(String extension) {
            if (acceptedExtensions.length == 0) {
                return canonicalExtension.equals(extension);
            }
            for (String accepted : acceptedExtensions) {
                if (accepted.equals(extension)) {
                    return true;
                }
            }
            return false;
        }
    }
}
