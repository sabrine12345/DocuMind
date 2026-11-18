package com.documind.template;

import com.documind.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "template_fields")
public class TemplateFieldEntity extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 140)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private FieldDataType dataType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected TemplateFieldEntity() {
    }

    public TemplateFieldEntity(UUID workspaceId, UUID templateId, String name, String label,
            FieldDataType dataType, boolean required, int displayOrder) {
        this.workspaceId = workspaceId;
        this.templateId = templateId;
        this.name = name;
        this.label = label;
        this.dataType = dataType;
        this.required = required;
        this.displayOrder = displayOrder;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public FieldDataType getDataType() {
        return dataType;
    }

    public boolean isRequired() {
        return required;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
