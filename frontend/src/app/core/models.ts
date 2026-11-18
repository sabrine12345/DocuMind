export type RoleName = 'SUPER_ADMIN' | 'ADMIN' | 'MANAGER' | 'VALIDATOR' | 'USER';

export type DocumentCategory =
  | 'INVOICE'
  | 'PURCHASE_ORDER'
  | 'CONTRACT'
  | 'BANK_STATEMENT'
  | 'RECEIPT'
  | 'SUPPLIER_DOCUMENT'
  | 'OTHER';

export type DocumentStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type ProcessingJobStatus = DocumentStatus;
export type DocumentValidationStatus = 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED';
export type ValidationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type ExtractionFieldStatus = 'PENDING' | 'EXTRACTED' | 'EDITED' | 'VALIDATED';
export type FieldDataType = 'TEXT' | 'NUMBER' | 'DATE' | 'CURRENCY' | 'BOOLEAN';

export const DOCUMENT_CATEGORIES: DocumentCategory[] = [
  'INVOICE', 'PURCHASE_ORDER', 'CONTRACT', 'BANK_STATEMENT', 'RECEIPT', 'SUPPLIER_DOCUMENT', 'OTHER'
];

export const FIELD_DATA_TYPES: FieldDataType[] = ['TEXT', 'NUMBER', 'DATE', 'CURRENCY', 'BOOLEAN'];

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  jobTitle: string | null;
  globalRole: RoleName;
  enabled: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface WorkspaceSummary {
  id: string;
  name: string;
  slug: string;
  role: RoleName;
  memberCount: number;
  createdAt: string;
}

export interface WorkspaceDetail {
  id: string;
  name: string;
  slug: string;
  role: RoleName;
  ownerId: string;
  memberCount: number;
  maxUploadSizeMb: number;
  storageProvider: string;
  storageBucket: string;
  notifyOnUpload: boolean;
  notifyOnValidation: boolean;
  createdAt: string;
}

export interface MemberResponse {
  id: string;
  userId: string;
  email: string;
  fullName: string;
  role: RoleName;
  owner: boolean;
  joinedAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserResponse;
  workspaces: WorkspaceSummary[];
  activeWorkspaceId: string | null;
}

export interface MeResponse {
  user: UserResponse;
  workspaces: WorkspaceSummary[];
  activeWorkspaceId: string | null;
}

export interface DocumentResponse {
  id: string;
  fileName: string;
  title: string | null;
  description: string | null;
  contentType: string;
  sizeBytes: number;
  pages: number;
  category: DocumentCategory;
  status: DocumentStatus;
  progress: number;
  validationStatus: DocumentValidationStatus;
  templateId: string | null;
  templateName: string | null;
  currentVersion: number;
  uploadedBy: string;
  uploadedByName: string;
  checksum: string;
  uploadedAt: string;
  updatedAt: string;
}

export interface DocumentVersionResponse {
  id: string;
  versionNumber: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  checksum: string;
  uploadedByName: string;
  createdAt: string;
}

export interface ExtractedFieldResponse {
  id: string;
  documentId: string;
  templateFieldId: string;
  fieldName: string;
  fieldLabel: string;
  dataType: FieldDataType;
  value: string | null;
  originalValue: string | null;
  confidence: number | null;
  sourcePage: number | null;
  status: ExtractionFieldStatus;
  editedBy: string | null;
  editedByName: string | null;
  editedAt: string | null;
}

export interface TemplateFieldResponse {
  id: string;
  name: string;
  label: string;
  dataType: FieldDataType;
  required: boolean;
  displayOrder: number;
}

export interface TemplateResponse {
  id: string;
  name: string;
  category: DocumentCategory;
  description: string | null;
  active: boolean;
  fieldCount: number;
  fields: TemplateFieldResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface TemplateFieldRequest {
  name: string;
  label: string;
  dataType: FieldDataType;
  required: boolean;
  displayOrder: number;
}

export interface TemplateRequest {
  name: string;
  category: DocumentCategory;
  description: string | null;
  active: boolean;
  fields: TemplateFieldRequest[];
}

export interface ProcessingJobResponse {
  id: string;
  documentId: string;
  fileName: string;
  category: DocumentCategory;
  status: ProcessingJobStatus;
  progress: number;
  attempt: number;
  startedAt: string | null;
  finishedAt: string | null;
  errorMessage: string | null;
  worker: string | null;
  createdAt: string;
}

export interface ValidationResponse {
  id: string;
  documentId: string;
  fileName: string;
  category: DocumentCategory;
  documentVersion: number;
  status: ValidationStatus;
  submittedBy: string;
  submittedByName: string;
  submittedAt: string;
  decidedBy: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionComment: string | null;
  commentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ValidationCommentResponse {
  id: string;
  authorId: string;
  authorName: string;
  message: string;
  createdAt: string;
}

export interface ValidationDetailResponse {
  validation: ValidationResponse;
  comments: ValidationCommentResponse[];
}

export interface AuditLogResponse {
  id: string;
  actorId: string | null;
  actorEmail: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  details: string | null;
  createdAt: string;
}

export interface CategoryStat {
  category: DocumentCategory;
  count: number;
}

export interface DailyUploadStat {
  date: string;
  uploaded: number;
}

export interface RecentDocument {
  id: string;
  fileName: string;
  category: DocumentCategory;
  status: DocumentStatus;
  validationStatus: DocumentValidationStatus;
  sizeBytes: number;
  uploadedAt: string;
}

export interface DashboardStats {
  totalDocuments: number;
  completedDocuments: number;
  processingDocuments: number;
  failedDocuments: number;
  storageBytes: number;
  templateCount: number;
  memberCount: number;
  pendingValidations: number;
  approvedDocuments: number;
  rejectedDocuments: number;
  categoryBreakdown: CategoryStat[];
  uploadsLast7Days: DailyUploadStat[];
  recentDocuments: RecentDocument[];
  recentActivity: AuditLogResponse[];
}

export interface DailyUsage {
  date: string;
  tokens: number;
  cost: number;
}

export interface ModelUsage {
  provider: string;
  model: string;
  events: number;
  tokens: number;
  cost: number;
}

export interface RecentUsage {
  id: string;
  documentId: string | null;
  provider: string;
  model: string;
  operation: string;
  totalTokens: number;
  cost: number;
  currency: string;
  occurredAt: string;
}

export interface UsageSummary {
  totalTokens: number;
  promptTokens: number;
  completionTokens: number;
  totalCost: number;
  currency: string;
  eventCount: number;
  daily: DailyUsage[];
  models: ModelUsage[];
  recent: RecentUsage[];
}

export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  requestId: string | null;
  fieldErrors: Record<string, string> | null;
}

export interface AddMemberRequest {
  email: string;
  role: RoleName;
}

export interface CreateWorkspaceRequest {
  name: string;
}

export interface UpdateWorkspaceRequest {
  name: string;
  maxUploadSizeMb: number;
  notifyOnUpload: boolean;
  notifyOnValidation: boolean;
}

export interface FieldValidationRequest {
  validated: boolean | null;
}
