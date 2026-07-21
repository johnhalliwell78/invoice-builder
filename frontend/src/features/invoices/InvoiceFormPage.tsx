import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useFieldArray, useForm, useWatch, type Control } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';

import { useCreateInvoice, useInvoice, useUpdateInvoice } from '@/hooks/useInvoices';
import { useCustomer } from '@/hooks/useCustomers';
import { CustomerCombobox } from '@/components/CustomerCombobox';
import type { InvoicePayload } from '@/api/invoices';
import type { Product } from '@/api/products';
import { ProductAutocompleteInput } from './ProductAutocompleteInput';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/PageHeader';
import { addDaysIso, formatCurrency, todayIso } from '@/lib/format';
import type { ProblemDetail } from '@/types/api';

const CURRENCIES = ['USD', 'EUR', 'GBP', 'CHF', 'JPY', 'CAD', 'AUD', 'SEK', 'NZD', 'SGD', 'CNY', 'INR'];

const numberString = (max?: number) =>
  z
    .string()
    .refine((s) => s !== '' && !Number.isNaN(Number(s)), 'Must be a number')
    .refine((s) => max == null || Number(s) <= max, `Must be ≤ ${max}`);

const schema = z.object({
  customerId: z.string().uuid(),
  currency: z.string().length(3),
  template: z.enum(['classic', 'modern']),
  issueDate: z.string().min(1),
  dueDate: z.string().min(1),
  discountAmount: z.string().optional(),
  notes: z.string().max(4000).optional(),
  terms: z.string().max(4000).optional(),
  lineItems: z
    .array(
      z.object({
        description: z.string().min(1).max(500),
        quantity: numberString(),
        unitPrice: numberString(),
        taxRate: numberString(100).optional(),
        discountPercent: numberString(100).optional(),
      }),
    )
    .min(1, 'Add at least one line item'),
});
type FormValues = z.infer<typeof schema>;

const blankLine = () => ({
  description: '',
  quantity: '1',
  unitPrice: '0.00',
  taxRate: '0',
  discountPercent: '0',
});

export default function InvoiceFormPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id && id !== 'new';
  const invoiceId = isEdit ? id : undefined;

  const { data: existing, isPending: loadingInvoice } = useInvoice(invoiceId);
  // Seed the combobox label when editing an existing invoice.
  const existingCustomer = useCustomer(existing?.customerId);
  const [pickedCustomer, setPickedCustomer] = useState<{ id: string; name: string } | null>(null);
  const create = useCreateInvoice();
  const update = useUpdateInvoice(invoiceId ?? '');

  const today = todayIso();
  const {
    register,
    handleSubmit,
    control,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      customerId: '',
      currency: 'USD',
      template: 'classic',
      issueDate: today,
      dueDate: addDaysIso(today, 30),
      discountAmount: '0.00',
      notes: '',
      terms: '',
      lineItems: [blankLine()],
    },
  });

  const { fields, append, remove } = useFieldArray({ control, name: 'lineItems' });
  const customerIdValue = useWatch({ control, name: 'customerId' });

  function applyProduct(index: number, product: Product) {
    setValue(`lineItems.${index}.description`, product.name, {
      shouldValidate: true,
      shouldDirty: true,
    });
    setValue(`lineItems.${index}.unitPrice`, String(product.unitPrice), { shouldDirty: true });
    setValue(`lineItems.${index}.taxRate`, String(product.taxRate), { shouldDirty: true });
  }

  useEffect(() => {
    if (existing) {
      reset({
        customerId: existing.customerId,
        currency: existing.currency,
        template: existing.template === 'modern' ? 'modern' : 'classic',
        issueDate: existing.issueDate,
        dueDate: existing.dueDate,
        discountAmount: existing.discountAmount,
        notes: existing.notes ?? '',
        terms: existing.terms ?? '',
        lineItems: existing.lineItems.map((li) => ({
          description: li.description,
          quantity: li.quantity,
          unitPrice: li.unitPrice,
          taxRate: li.taxRate,
          discountPercent: li.discountPercent,
        })),
      });
    }
  }, [existing, reset]);

  async function onSubmit(values: FormValues) {
    const payload: InvoicePayload = {
      customerId: values.customerId,
      currency: values.currency,
      template: values.template,
      issueDate: values.issueDate,
      dueDate: values.dueDate,
      discountAmount: values.discountAmount,
      notes: values.notes || undefined,
      terms: values.terms || undefined,
      lineItems: values.lineItems.map((li) => ({
        description: li.description.trim(),
        quantity: li.quantity,
        unitPrice: li.unitPrice,
        taxRate: li.taxRate || '0',
        discountPercent: li.discountPercent || '0',
      })),
    };

    try {
      const saved = isEdit && invoiceId
        ? await update.mutateAsync(payload)
        : await create.mutateAsync(payload);
      toast.success(isEdit ? t('invoices.updated') : t('invoices.created'));
      navigate(`/invoices/${saved.id}`);
    } catch (err) {
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('auth.errors.default'));
    }
  }

  if (isEdit && loadingInvoice) {
    return <div className="text-muted-foreground">{t('common.loading')}</div>;
  }

  return (
    <div>
      <PageHeader
        title={isEdit ? t('invoices.editTitle') : t('invoices.createTitle')}
        actions={
          <Button variant="outline" onClick={() => navigate('/invoices')}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            {t('common.back')}
          </Button>
        }
      />

      <form onSubmit={(e) => void handleSubmit(onSubmit)(e)} className="grid max-w-4xl gap-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>{t('invoices.section.metadata')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5 sm:col-span-2">
              <Label>{t('invoices.fields.customer')} *</Label>
              <CustomerCombobox
                selectedId={customerIdValue}
                selectedName={pickedCustomer?.name ?? existingCustomer.data?.name}
                invalid={!!errors.customerId}
                placeholder={t('invoices.fields.customerPlaceholder')}
                onSelect={(c) => {
                  setPickedCustomer(c ? { id: c.id, name: c.name } : null);
                  setValue('customerId', c?.id ?? '', { shouldValidate: true, shouldDirty: true });
                }}
              />
              {errors.customerId && (
                <p className="text-xs text-destructive">{t('invoices.fields.customerRequired')}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label>{t('invoices.fields.currency')}</Label>
              <select
                className="h-10 w-full rounded-md border bg-background px-3 text-sm"
                {...register('currency')}
              >
                {CURRENCIES.map((code) => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label>{t('invoices.fields.discountAmount')}</Label>
              <Input inputMode="decimal" {...register('discountAmount')} />
            </div>
            <div className="space-y-1.5">
              <Label>{t('invoices.fields.issueDate')} *</Label>
              <Input type="date" aria-invalid={!!errors.issueDate} {...register('issueDate')} />
            </div>
            <div className="space-y-1.5">
              <Label>{t('invoices.fields.dueDate')} *</Label>
              <Input type="date" aria-invalid={!!errors.dueDate} {...register('dueDate')} />
            </div>
            <div className="space-y-1.5">
              <Label>{t('invoices.fields.template')}</Label>
              <select
                className="h-10 w-full rounded-md border bg-background px-3 text-sm"
                {...register('template')}
              >
                <option value="classic">{t('invoices.template.classic')}</option>
                <option value="modern">{t('invoices.template.modern')}</option>
              </select>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>{t('invoices.section.lineItems')}</CardTitle>
            <Button type="button" variant="outline" size="sm" onClick={() => append(blankLine())}>
              <Plus className="mr-1 h-4 w-4" />
              {t('invoices.addLineItem')}
            </Button>
          </CardHeader>
          <CardContent className="space-y-3">
            {fields.map((field, index) => (
              <div key={field.id} className="grid grid-cols-12 items-end gap-2">
                <div className="col-span-12 space-y-1.5 sm:col-span-5">
                  {index === 0 && <Label className="text-xs">{t('invoices.fields.description')}</Label>}
                  <ProductAutocompleteInput
                    placeholder={t('invoices.fields.descriptionPlaceholder')}
                    invalid={!!errors.lineItems?.[index]?.description}
                    registration={register(`lineItems.${index}.description`)}
                    onPick={(product) => applyProduct(index, product)}
                  />
                </div>
                <div className="col-span-3 space-y-1.5 sm:col-span-1">
                  {index === 0 && <Label className="text-xs">{t('invoices.fields.qty')}</Label>}
                  <Input inputMode="decimal" {...register(`lineItems.${index}.quantity`)} />
                </div>
                <div className="col-span-3 space-y-1.5 sm:col-span-2">
                  {index === 0 && <Label className="text-xs">{t('invoices.fields.unitPrice')}</Label>}
                  <Input inputMode="decimal" {...register(`lineItems.${index}.unitPrice`)} />
                </div>
                <div className="col-span-3 space-y-1.5 sm:col-span-1">
                  {index === 0 && <Label className="text-xs">{t('invoices.fields.taxPct')}</Label>}
                  <Input inputMode="decimal" {...register(`lineItems.${index}.taxRate`)} />
                </div>
                <div className="col-span-3 space-y-1.5 sm:col-span-2">
                  {index === 0 && <Label className="text-xs">{t('invoices.fields.discPct')}</Label>}
                  <Input inputMode="decimal" {...register(`lineItems.${index}.discountPercent`)} />
                </div>
                <div className="col-span-12 sm:col-span-1">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={t('common.delete')}
                    disabled={fields.length === 1}
                    onClick={() => remove(index)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            ))}
            {errors.lineItems?.message && (
              <p className="text-xs text-destructive">{errors.lineItems.message}</p>
            )}
            <TotalsPanel control={control} locale={i18n.language} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('invoices.section.notesAndTerms')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>{t('invoices.fields.notes')}</Label>
              <Textarea rows={3} {...register('notes')} />
            </div>
            <div className="space-y-1.5">
              <Label>{t('invoices.fields.terms')}</Label>
              <Textarea rows={3} {...register('terms')} />
            </div>
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => navigate('/invoices')}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? t('common.saving') : t('invoices.saveDraft')}
          </Button>
        </div>
      </form>
    </div>
  );
}

function TotalsPanel({ control, locale }: { control: Control<FormValues>; locale: string }) {
  const { t } = useTranslation();
  const lineItems = useWatch({ control, name: 'lineItems' });
  const discount = useWatch({ control, name: 'discountAmount' });
  const currency = useWatch({ control, name: 'currency' }) || 'USD';

  const totals = useMemo(() => computeTotals(lineItems, discount), [lineItems, discount]);

  return (
    <div className="ml-auto mt-4 max-w-xs space-y-1 rounded-md bg-muted/40 p-4 text-sm">
      <Row label={t('invoices.totals.subtotal')} value={formatCurrency(totals.subtotal, currency, locale)} />
      <Row label={t('invoices.totals.tax')} value={formatCurrency(totals.tax, currency, locale)} />
      <Row label={t('invoices.totals.discount')} value={formatCurrency(totals.discount, currency, locale)} />
      <div className="my-2 border-t" />
      <Row
        label={t('invoices.totals.total')}
        value={formatCurrency(totals.total, currency, locale)}
        bold
      />
    </div>
  );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <div className={`flex justify-between ${bold ? 'font-semibold' : ''}`}>
      <span className="text-muted-foreground">{label}</span>
      <span className="tabular-nums">{value}</span>
    </div>
  );
}

function computeTotals(lineItems: FormValues['lineItems'], discountAmount: string | undefined) {
  let subtotal = 0;
  let tax = 0;
  for (const li of lineItems ?? []) {
    const qty = num(li.quantity);
    const unit = num(li.unitPrice);
    const disc = num(li.discountPercent);
    const taxRate = num(li.taxRate);
    const lineAmount = qty * unit * (1 - disc / 100);
    subtotal += lineAmount;
    tax += lineAmount * (taxRate / 100);
  }
  const flatDiscount = Math.max(0, num(discountAmount));
  const total = Math.max(0, subtotal + tax - flatDiscount);
  return { subtotal, tax, discount: flatDiscount, total };
}

function num(v: string | undefined): number {
  if (!v) return 0;
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}
