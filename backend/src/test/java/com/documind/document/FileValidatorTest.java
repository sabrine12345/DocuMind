package com.documind.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.documind.common.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileValidatorTest {

    private final FileValidator validator = new FileValidator();

    @Test
    void acceptsPdfByMagicBytes() {
        MockMultipartFile file = new MockMultipartFile("files", "Invoice.PDF", "application/pdf",
                "%PDF-1.7\nbody".getBytes());

        FileValidator.DetectedFile detected = validator.validate(file, 10);

        assertThat(detected.originalName()).isEqualTo("Invoice.PDF");
        assertThat(detected.contentType()).isEqualTo("application/pdf");
        assertThat(detected.canonicalExtension()).isEqualTo("pdf");
    }

    @Test
    void rejectsContentThatDoesNotMatchExtension() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("files", "invoice.pdf", "application/pdf", png);

        assertThatThrownBy(() -> validator.validate(file, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsFilesAboveWorkspaceLimit() {
        byte[] content = new byte[1024 * 1024 + 1];
        content[0] = '%';
        content[1] = 'P';
        content[2] = 'D';
        content[3] = 'F';
        content[4] = '-';
        MockMultipartFile file = new MockMultipartFile("files", "big.pdf", "application/pdf", content);

        assertThatThrownBy(() -> validator.validate(file, 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Maximum file size");
    }

    @Test
    void stripsDirectoriesFromUploadedNames() {
        MockMultipartFile file = new MockMultipartFile("files", "..\\evil.pdf", "application/pdf",
                "%PDF-1.4".getBytes());

        assertThat(validator.safeName(file)).isEqualTo("evil.pdf");
    }
}
