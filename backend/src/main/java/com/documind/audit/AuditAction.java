package com.documind.audit;

/**
 * Canonical audit action names.
 */
public final class AuditAction {

    public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
    public static final String DOCUMENT_DOWNLOADED = "DOCUMENT_DOWNLOADED";
    public static final String DOCUMENT_PREVIEWED = "DOCUMENT_PREVIEWED";
    public static final String DOCUMENT_UPDATED = "DOCUMENT_UPDATED";
    public static final String DOCUMENT_DELETED = "DOCUMENT_DELETED";
    public static final String DOCUMENT_VERSION_ADDED = "DOCUMENT_VERSION_ADDED";
    public static final String PROCESSING_RETRIED = "PROCESSING_RETRIED";
    public static final String PROCESSING_COMPLETED = "PROCESSING_COMPLETED";
    public static final String PROCESSING_FAILED = "PROCESSING_FAILED";
    public static final String TEMPLATE_CREATED = "TEMPLATE_CREATED";
    public static final String TEMPLATE_UPDATED = "TEMPLATE_UPDATED";
    public static final String TEMPLATE_DELETED = "TEMPLATE_DELETED";
    public static final String FIELD_UPDATED = "EXTRACTED_FIELD_UPDATED";
    public static final String FIELD_VALIDATED = "EXTRACTED_FIELD_VALIDATED";
    public static final String FIELDS_PREPARED = "EXTRACTED_FIELDS_PREPARED";
    public static final String VALIDATION_SUBMITTED = "VALIDATION_SUBMITTED";
    public static final String VALIDATION_APPROVED = "VALIDATION_APPROVED";
    public static final String VALIDATION_REJECTED = "VALIDATION_REJECTED";
    public static final String VALIDATION_COMMENTED = "VALIDATION_COMMENTED";
    public static final String MEMBER_ADDED = "MEMBER_ADDED";
    public static final String MEMBER_ROLE_CHANGED = "MEMBER_ROLE_CHANGED";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String WORKSPACE_UPDATED = "WORKSPACE_UPDATED";

    private AuditAction() {
    }
}
