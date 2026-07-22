import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  approveInvoice,
  cancelInvoice,
  convertEstimate,
  declineInvoice,
  createInvoice,
  deleteInvoice,
  duplicateInvoice,
  getEmailPreview,
  getInvoice,
  listInvoices,
  listPayments,
  listReminders,
  markPaid,
  recordPayment,
  resendInvoice,
  sendInvoice,
  updateInvoice,
  type InvoiceListParams,
  type InvoicePayload,
  type PaymentPayload,
  type SendInvoicePayload,
} from '@/api/invoices';

const KEY = ['invoices'] as const;

export function useInvoiceList(params: InvoiceListParams) {
  return useQuery({
    queryKey: [...KEY, 'list', params],
    queryFn: () => listInvoices(params),
    placeholderData: keepPreviousData,
  });
}

export function useInvoice(id: string | undefined) {
  return useQuery({
    queryKey: [...KEY, 'detail', id],
    queryFn: () => getInvoice(id as string),
    enabled: !!id,
  });
}

export function useCreateInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: InvoicePayload) => createInvoice(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useUpdateInvoice(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: InvoicePayload) => updateInvoice(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDuplicateInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => duplicateInvoice(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDeleteInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteInvoice(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useSendInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; payload?: SendInvoicePayload }) =>
      sendInvoice(args.id, args.payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useEmailPreview(id: string, enabled: boolean) {
  return useQuery({
    queryKey: [...KEY, 'email-preview', id],
    queryFn: () => getEmailPreview(id),
    enabled,
  });
}

export function useReminders(id: string | undefined) {
  return useQuery({
    queryKey: [...KEY, 'reminders', id],
    queryFn: () => listReminders(id as string),
    enabled: !!id,
  });
}

export function useResendInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; payload?: SendInvoicePayload }) =>
      resendInvoice(args.id, args.payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useApproveEstimate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => approveInvoice(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useDeclineEstimate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => declineInvoice(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useConvertEstimate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => convertEstimate(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function usePayments(id: string | undefined) {
  return useQuery({
    queryKey: [...KEY, 'payments', id],
    queryFn: () => listPayments(id as string),
    enabled: !!id,
  });
}

export function useRecordPayment(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: PaymentPayload) => recordPayment(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useMarkPaid() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => markPaid(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}

export function useCancelInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => cancelInvoice(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
