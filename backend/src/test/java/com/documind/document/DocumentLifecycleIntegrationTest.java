package com.documind.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.documind.dashboard.DashboardStatsResponse;
import com.documind.document.dto.DocumentResponse;
import com.documind.extraction.ExtractionFieldStatus;
import com.documind.extraction.dto.ExtractedFieldResponse;
import com.documind.security.dto.AuthResponse;
import com.documind.security.dto.RegisterRequest;
import com.documind.template.dto.TemplateFieldResponse;
import com.documind.template.dto.TemplateResponse;
import com.documind.validation.ValidationStatus;
import com.documind.validation.dto.ValidationDetailResponse;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * End to end backend verification: upload, asynchronous preparation, template
 * driven extraction, manual validation workflow and dashboard statistics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DocumentLifecycleIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void uploadPrepareExtractValidateAndReport() throws Exception {
        String token = register();
        TemplateResponse invoice = findTemplate(token, "Invoice");
        byte[] pdf = samplePdf();

        DocumentResponse document = upload(token, pdf, invoice.id());
        assertThat(document.templateId()).isEqualTo(invoice.id());

        DocumentResponse processed = awaitProcessed(token, document.id());
        assertThat(processed.pages()).isEqualTo(1);
        assertThat(processed.checksum()).isNotBlank();
        assertThat(processed.progress()).isEqualTo(100);

        ExtractedFieldResponse[] fields = fields(token, document.id());
        assertThat(fields).hasSize(invoice.fieldCount());
        assertThat(fields).allSatisfy(field -> {
            assertThat(field.value()).isNull();
            assertThat(field.status()).isEqualTo(ExtractionFieldStatus.PENDING);
        });

        for (TemplateFieldResponse definition : invoice.fields()) {
            if (!definition.required()) {
                continue;
            }
            ExtractedFieldResponse field = Arrays.stream(fields)
                    .filter(candidate -> candidate.fieldName().equals(definition.name()))
                    .findFirst()
                    .orElseThrow();
            String value = sampleValue(definition);
            ResponseEntity<ExtractedFieldResponse> updated = rest.exchange("/api/fields/" + field.id(),
                    HttpMethod.PUT, new HttpEntity<>(Map.of("value", value), auth(token)),
                    ExtractedFieldResponse.class);
            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody()).isNotNull();
            assertThat(updated.getBody().value()).isEqualTo(value);
            assertThat(updated.getBody().status()).isEqualTo(ExtractionFieldStatus.EDITED);
            assertThat(updated.getBody().editedByName()).isEqualTo("Jane Tester");
        }

        ResponseEntity<ValidationDetailResponse> submitted = rest.exchange(
                "/api/documents/" + document.id() + "/validations", HttpMethod.POST,
                new HttpEntity<>(Map.of("comment", "Ready for review"), auth(token)),
                ValidationDetailResponse.class);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitted.getBody()).isNotNull();
        assertThat(submitted.getBody().validation().status()).isEqualTo(ValidationStatus.PENDING);
        assertThat(submitted.getBody().comments()).hasSize(1);
        UUID validationId = submitted.getBody().validation().id();
        assertThat(getDocument(token, document.id()).validationStatus())
                .isEqualTo(DocumentValidationStatus.PENDING);

        ResponseEntity<ValidationDetailResponse> approved = rest.exchange("/api/validations/" + validationId
                        + "/approve", HttpMethod.POST,
                new HttpEntity<>(Map.of("comment", "Validated by test"), auth(token)),
                ValidationDetailResponse.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody()).isNotNull();
        assertThat(approved.getBody().validation().status()).isEqualTo(ValidationStatus.APPROVED);
        assertThat(getDocument(token, document.id()).validationStatus())
                .isEqualTo(DocumentValidationStatus.APPROVED);

        DashboardStatsResponse stats = rest.exchange("/api/dashboard/stats", HttpMethod.GET,
                new HttpEntity<>(auth(token)), DashboardStatsResponse.class).getBody();
        assertThat(stats).isNotNull();
        assertThat(stats.totalDocuments()).isGreaterThanOrEqualTo(1);
        assertThat(stats.completedDocuments()).isGreaterThanOrEqualTo(1);
        assertThat(stats.storageBytes()).isPositive();
        assertThat(stats.approvedDocuments()).isGreaterThanOrEqualTo(1);
        assertThat(stats.pendingValidations()).isZero();
        assertThat(stats.recentDocuments()).isNotEmpty();
        assertThat(stats.uploadsLast7Days()).hasSize(7);

        ResponseEntity<byte[]> preview = rest.exchange("/api/documents/" + document.id() + "/preview",
                HttpMethod.GET, new HttpEntity<>(auth(token)), byte[].class);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getHeaders().getContentType()).isNotNull();
        assertThat(preview.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PDF)).isTrue();
        assertThat(preview.getBody()).isNotEmpty();
    }

    private String register() {
        RegisterRequest request = new RegisterRequest("lifecycle-" + UUID.randomUUID() + "@documind.test",
                "Password123", "Jane Tester", "Operations", "Lifecycle workspace");
        ResponseEntity<AuthResponse> response = rest.postForEntity("/api/auth/register", request,
                AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().accessToken();
    }

    private TemplateResponse findTemplate(String token, String name) {
        TemplateResponse[] templates = rest.exchange("/api/templates", HttpMethod.GET,
                new HttpEntity<>(auth(token)), TemplateResponse[].class).getBody();
        assertThat(templates).isNotEmpty();
        return Arrays.stream(templates)
                .filter(template -> template.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private DocumentResponse upload(String token, byte[] pdf, UUID templateId) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_PDF);
        ByteArrayResource resource = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "invoice.pdf";
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("files", new HttpEntity<>(resource, partHeaders));
        HttpHeaders headers = auth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<DocumentResponse[]> response = rest.exchange(
                "/api/documents?templateId=" + templateId, HttpMethod.POST,
                new HttpEntity<>(form, headers), DocumentResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).hasSize(1);
        return response.getBody()[0];
    }

    private DocumentResponse awaitProcessed(String token, UUID documentId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        DocumentResponse latest = null;
        while (System.nanoTime() < deadline) {
            latest = getDocument(token, documentId);
            if (latest.status() == DocumentStatus.COMPLETED) {
                return latest;
            }
            if (latest.status() == DocumentStatus.FAILED) {
                fail("Processing failed for document " + documentId);
            }
            Thread.sleep(200);
        }
        fail("Document " + documentId + " was not processed in time (last status: "
                + (latest == null ? "unknown" : latest.status()) + ")");
        return null;
    }

    private DocumentResponse getDocument(String token, UUID documentId) {
        return rest.exchange("/api/documents/" + documentId, HttpMethod.GET,
                new HttpEntity<>(auth(token)), DocumentResponse.class).getBody();
    }

    private ExtractedFieldResponse[] fields(String token, UUID documentId) {
        ExtractedFieldResponse[] fields = rest.exchange("/api/documents/" + documentId + "/fields",
                HttpMethod.GET, new HttpEntity<>(auth(token)), ExtractedFieldResponse[].class).getBody();
        assertThat(fields).isNotNull();
        return fields;
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String sampleValue(TemplateFieldResponse definition) {
        return switch (definition.dataType()) {
            case DATE -> "2026-11-16";
            case NUMBER, CURRENCY -> "1250.50";
            case BOOLEAN -> "true";
            case TEXT -> "Sample " + definition.name();
        };
    }

    private byte[] samplePdf() throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            pdf.save(output);
            return output.toByteArray();
        }
    }
}
