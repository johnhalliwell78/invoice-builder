import { api } from './client';
import type { ApiEnvelope } from '@/types/api';

export interface TaxSummaryRow {
  currency: string;
  taxRate: string;
  net: string;
  tax: string;
  gross: string;
}

export async function getTaxSummary(from: string, to: string): Promise<TaxSummaryRow[]> {
  const res = await api.get<ApiEnvelope<TaxSummaryRow[]>>('/api/v1/reports/tax-summary', {
    params: { from, to },
  });
  return res.data.data;
}

/**
 * Downloads a CSV export. Fetched through the authenticated client rather
 * than a plain link so the bearer token is attached; the blob is then handed
 * to the browser as a file.
 */
export async function downloadCsv(path: string, params: Record<string, string>, filename: string) {
  const res = await api.get(path, { params, responseType: 'blob' });
  const url = window.URL.createObjectURL(res.data as Blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
}
