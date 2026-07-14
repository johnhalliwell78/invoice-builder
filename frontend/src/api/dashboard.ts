import { api } from './client';
import type { ApiEnvelope, InvoiceStatus } from '@/types/api';

export interface CurrencyTile {
  currency: string;
  outstanding: string;
  overdue: string;
  paidThisMonth: string;
}

export interface MonthAmount {
  month: string;
  currency: string;
  total: string;
}

export interface MonthCount {
  month: string;
  count: number;
}

export interface RecentInvoice {
  id: string;
  invoiceNumber: string;
  status: InvoiceStatus;
  total: string;
  currency: string;
  customerName: string;
  updatedAt: string;
}

export interface Dashboard {
  tiles: CurrencyTile[];
  statusCounts: Partial<Record<InvoiceStatus, number>>;
  revenueByMonth: MonthAmount[];
  customersByMonth: MonthCount[];
  recentInvoices: RecentInvoice[];
}

export async function getDashboard(): Promise<Dashboard> {
  const res = await api.get<ApiEnvelope<Dashboard>>('/api/v1/dashboard');
  return res.data.data;
}
