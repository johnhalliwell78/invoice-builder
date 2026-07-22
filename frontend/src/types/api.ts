export type Role = 'OWNER' | 'ADMIN' | 'MEMBER';
export type AuthProvider = 'LOCAL' | 'GOOGLE' | 'GITHUB';

export interface User {
  id: string;
  tenantId: string;
  email: string;
  fullName: string;
  avatarUrl: string | null;
  role: Role;
  provider: AuthProvider;
  locale: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

export interface ApiEnvelope<T> {
  data: T;
  meta?: Record<string, unknown>;
}

export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  details?: string[];
  timestamp?: string;
}

export interface Address {
  street?: string;
  city?: string;
  state?: string;
  zip?: string;
  country?: string;
}

export interface Customer {
  id: string;
  name: string;
  email: string | null;
  phone: string | null;
  company: string | null;
  address: Address | null;
  taxId: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export type InvoiceStatus =
  | 'DRAFT'
  | 'SENT'
  | 'VIEWED'
  | 'PAID'
  | 'OVERDUE'
  | 'CANCELLED'
  | 'APPROVED'
  | 'DECLINED';

export interface LineItem {
  id?: string;
  description: string;
  quantity: string;
  unitPrice: string;
  taxRate: string;
  discountPercent: string;
  amount?: string;
  sortOrder?: number;
}

export type DocType = 'INVOICE' | 'ESTIMATE';

export interface InvoiceListItem {
  id: string;
  customerId: string;
  customerName: string | null;
  invoiceNumber: string;
  docType: DocType;
  status: InvoiceStatus;
  currency: string;
  total: string;
  amountPaid: string;
  issueDate: string;
  dueDate: string;
}

export interface Invoice {
  id: string;
  customerId: string;
  invoiceNumber: string;
  docType: DocType;
  convertedInvoiceId: string | null;
  status: InvoiceStatus;
  currency: string;
  subtotal: string;
  taxTotal: string;
  discountAmount: string;
  total: string;
  amountPaid: string;
  issueDate: string;
  dueDate: string;
  notes: string | null;
  terms: string | null;
  template: string;
  publicToken: string | null;
  sentAt: string | null;
  viewedAt: string | null;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
  lineItems: LineItem[];
}
