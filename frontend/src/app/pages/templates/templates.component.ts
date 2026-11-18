import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { apiErrorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import {
  DOCUMENT_CATEGORIES,
  DocumentCategory,
  FIELD_DATA_TYPES,
  FieldDataType,
  TemplateFieldRequest,
  TemplateRequest,
  TemplateResponse
} from '../../core/models';
import { TemplatesService } from '../../core/templates.service';
import { humanize } from '../../shared/format';
import { IconModule } from '../../shared/icons';

interface FieldDraft {
  name: string;
  label: string;
  dataType: FieldDataType;
  required: boolean;
}

@Component({
  selector: 'app-templates',
  standalone: true,
  imports: [CommonModule, IconModule],
  templateUrl: './templates.component.html',
  styleUrl: './templates.component.css'
})
export class TemplatesComponent {
  private readonly templatesService = inject(TemplatesService);
  readonly auth = inject(AuthService);

  readonly categories = DOCUMENT_CATEGORIES;
  readonly dataTypes = FIELD_DATA_TYPES;

  readonly templates = signal<TemplateResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly notice = signal('');
  readonly expanded = signal<string | null>(null);

  readonly editorOpen = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly formName = signal('');
  readonly formCategory = signal<DocumentCategory>('OTHER');
  readonly formDescription = signal('');
  readonly formActive = signal(true);
  readonly formFields = signal<FieldDraft[]>([]);
  readonly saving = signal(false);

  readonly templateToDelete = signal<TemplateResponse | null>(null);
  readonly deleting = signal(false);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.templatesService.list().subscribe({
      next: templates => {
        this.templates.set(templates);
        this.loading.set(false);
      },
      error: error => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to load templates.'));
      }
    });
  }

  toggleExpanded(template: TemplateResponse): void {
    this.expanded.set(this.expanded() === template.id ? null : template.id);
  }

  openCreate(): void {
    this.editingId.set(null);
    this.formName.set('');
    this.formCategory.set('OTHER');
    this.formDescription.set('');
    this.formActive.set(true);
    this.formFields.set([this.blankField(0)]);
    this.editorOpen.set(true);
    this.error.set('');
  }

  openEdit(template: TemplateResponse): void {
    this.editingId.set(template.id);
    this.formName.set(template.name);
    this.formCategory.set(template.category);
    this.formDescription.set(template.description ?? '');
    this.formActive.set(template.active);
    this.formFields.set(template.fields
      .slice()
      .sort((left, right) => left.displayOrder - right.displayOrder)
      .map(field => ({
        name: field.name,
        label: field.label,
        dataType: field.dataType,
        required: field.required
      })));
    this.editorOpen.set(true);
    this.error.set('');
  }

  closeEditor(): void {
    this.editorOpen.set(false);
    this.editingId.set(null);
  }

  addField(): void {
    this.formFields.update(fields => [...fields, this.blankField(fields.length)]);
  }

  removeField(index: number): void {
    this.formFields.update(fields => fields.filter((_field, position) => position !== index));
  }

  moveField(index: number, delta: number): void {
    this.formFields.update(fields => {
      const target = index + delta;
      if (target < 0 || target >= fields.length) {
        return fields;
      }
      const copy = fields.slice();
      const [moved] = copy.splice(index, 1);
      copy.splice(target, 0, moved);
      return copy;
    });
  }

  updateName(index: number, value: string): void {
    this.formFields.update(fields => fields.map((field, position) => {
      if (position !== index) {
        return field;
      }
      const next = { ...field, name: value };
      if (!field.label.trim() && value.trim()) {
        next.label = humanize(value.trim().replace(/[_-]+/g, ' '));
      }
      return next;
    }));
  }

  updateLabel(index: number, value: string): void {
    this.formFields.update(fields => fields.map((field, position) =>
      position === index ? { ...field, label: value } : field));
  }

  updateType(index: number, value: string): void {
    this.formFields.update(fields => fields.map((field, position) =>
      position === index ? { ...field, dataType: value as FieldDataType } : field));
  }

  toggleRequired(index: number): void {
    this.formFields.update(fields => fields.map((field, position) =>
      position === index ? { ...field, required: !field.required } : field));
  }

  onCategoryChange(event: Event): void {
    this.formCategory.set((event.target as HTMLSelectElement).value as DocumentCategory);
  }

  save(): void {
    const name = this.formName().trim();
    if (!name) {
      this.error.set('Template name is required.');
      return;
    }
    const drafts = this.formFields();
    const seen = new Set<string>();
    const fields: TemplateFieldRequest[] = [];
    for (const draft of drafts) {
      const fieldName = draft.name.trim().toLowerCase().replace(/\s+/g, '_');
      if (!fieldName) {
        this.error.set('Every field needs a name.');
        return;
      }
      if (!draft.label.trim()) {
        this.error.set(`Field "${fieldName}" needs a label.`);
        return;
      }
      if (seen.has(fieldName)) {
        this.error.set(`Field name "${fieldName}" is used twice.`);
        return;
      }
      seen.add(fieldName);
      fields.push({
        name: fieldName,
        label: draft.label.trim(),
        dataType: draft.dataType,
        required: draft.required,
        displayOrder: fields.length
      });
    }

    const request: TemplateRequest = {
      name,
      category: this.formCategory(),
      description: this.formDescription().trim() || null,
      active: this.formActive(),
      fields
    };

    this.saving.set(true);
    this.error.set('');
    const id = this.editingId();
    const action = id
      ? this.templatesService.update(id, request)
      : this.templatesService.create(request);
    action.subscribe({
      next: template => {
        this.saving.set(false);
        this.editorOpen.set(false);
        this.editingId.set(null);
        this.notice.set(id ? `Template "${template.name}" updated.` : `Template "${template.name}" created.`);
        this.reload();
      },
      error: error => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(error, 'Unable to save the template.'));
      }
    });
  }

  confirmDelete(): void {
    const template = this.templateToDelete();
    if (!template) {
      return;
    }
    this.deleting.set(true);
    this.templatesService.remove(template.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.templateToDelete.set(null);
        this.notice.set(`Template "${template.name}" deleted.`);
        this.reload();
      },
      error: error => {
        this.deleting.set(false);
        this.templateToDelete.set(null);
        this.error.set(apiErrorMessage(error, 'Unable to delete the template.'));
      }
    });
  }

  label(value: string | null | undefined): string {
    return humanize(value);
  }

  private blankField(_order: number): FieldDraft {
    return { name: '', label: '', dataType: 'TEXT', required: false };
  }
}
