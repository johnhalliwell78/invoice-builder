import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';

import { useCreateCustomer, useCustomer, useUpdateCustomer } from '@/hooks/useCustomers';
import type { CustomerPayload } from '@/api/customers';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/PageHeader';
import type { ProblemDetail } from '@/types/api';

const schema = z.object({
  name: z.string().min(1).max(255),
  email: z.string().email().max(255).optional().or(z.literal('')),
  phone: z.string().max(50).optional().or(z.literal('')),
  company: z.string().max(255).optional().or(z.literal('')),
  taxId: z.string().max(100).optional().or(z.literal('')),
  notes: z.string().max(4000).optional().or(z.literal('')),
  address: z
    .object({
      street: z.string().max(255).optional().or(z.literal('')),
      city: z.string().max(255).optional().or(z.literal('')),
      state: z.string().max(255).optional().or(z.literal('')),
      zip: z.string().max(20).optional().or(z.literal('')),
      country: z.string().max(100).optional().or(z.literal('')),
    })
    .optional(),
});
type FormValues = z.infer<typeof schema>;

export default function CustomerFormPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id && id !== 'new';
  const customerId = isEdit ? id : undefined;

  const { data: existing, isPending: loading } = useCustomer(customerId);
  const create = useCreateCustomer();
  const update = useUpdateCustomer(customerId ?? '');

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      email: '',
      phone: '',
      company: '',
      taxId: '',
      notes: '',
      address: { street: '', city: '', state: '', zip: '', country: '' },
    },
  });

  useEffect(() => {
    if (existing) {
      reset({
        name: existing.name,
        email: existing.email ?? '',
        phone: existing.phone ?? '',
        company: existing.company ?? '',
        taxId: existing.taxId ?? '',
        notes: existing.notes ?? '',
        address: {
          street: existing.address?.street ?? '',
          city: existing.address?.city ?? '',
          state: existing.address?.state ?? '',
          zip: existing.address?.zip ?? '',
          country: existing.address?.country ?? '',
        },
      });
    }
  }, [existing, reset]);

  async function onSubmit(values: FormValues) {
    const payload: CustomerPayload = stripBlanks({
      ...values,
      address: hasAnyAddressField(values.address) ? values.address : undefined,
    });
    try {
      if (isEdit && customerId) {
        await update.mutateAsync(payload);
        toast.success(t('customers.updated'));
      } else {
        await create.mutateAsync(payload);
        toast.success(t('customers.created'));
      }
      navigate('/customers');
    } catch (err) {
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('auth.errors.default'));
    }
  }

  if (isEdit && loading) {
    return <div className="text-muted-foreground">{t('common.loading')}</div>;
  }

  return (
    <div>
      <PageHeader
        title={isEdit ? t('customers.editTitle') : t('customers.createTitle')}
        actions={
          <Button variant="outline" onClick={() => navigate('/customers')}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            {t('common.back')}
          </Button>
        }
      />

      <form onSubmit={(e) => void handleSubmit(onSubmit)(e)} className="grid max-w-3xl gap-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>{t('customers.section.contact')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <Field label={t('customers.fields.name') + ' *'} error={errors.name?.message}>
              <Input aria-invalid={!!errors.name} {...register('name')} />
            </Field>
            <Field label={t('customers.fields.company')}>
              <Input {...register('company')} />
            </Field>
            <Field label={t('customers.fields.email')} error={errors.email?.message}>
              <Input type="email" aria-invalid={!!errors.email} {...register('email')} />
            </Field>
            <Field label={t('customers.fields.phone')}>
              <Input {...register('phone')} />
            </Field>
            <Field label={t('customers.fields.taxId')}>
              <Input {...register('taxId')} />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('customers.section.address')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <Field label={t('customers.fields.street')} className="sm:col-span-2">
              <Input {...register('address.street')} />
            </Field>
            <Field label={t('customers.fields.city')}>
              <Input {...register('address.city')} />
            </Field>
            <Field label={t('customers.fields.state')}>
              <Input {...register('address.state')} />
            </Field>
            <Field label={t('customers.fields.zip')}>
              <Input {...register('address.zip')} />
            </Field>
            <Field label={t('customers.fields.country')}>
              <Input {...register('address.country')} />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('customers.section.notes')}</CardTitle>
          </CardHeader>
          <CardContent>
            <Textarea rows={4} {...register('notes')} />
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => navigate('/customers')}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? t('common.saving') : t('common.save')}
          </Button>
        </div>
      </form>
    </div>
  );
}

function Field({
  label,
  error,
  children,
  className,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={`space-y-1.5 ${className ?? ''}`}>
      <Label>{label}</Label>
      {children}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}

function hasAnyAddressField(addr?: {
  street?: string;
  city?: string;
  state?: string;
  zip?: string;
  country?: string;
}): boolean {
  return !!addr && Object.values(addr).some((v) => v && v.trim().length > 0);
}

function stripBlanks(payload: FormValues): CustomerPayload {
  const result: CustomerPayload = { name: payload.name.trim() };
  if (payload.email) result.email = payload.email.trim();
  if (payload.phone) result.phone = payload.phone.trim();
  if (payload.company) result.company = payload.company.trim();
  if (payload.taxId) result.taxId = payload.taxId.trim();
  if (payload.notes) result.notes = payload.notes.trim();
  if (payload.address) {
    const a = payload.address;
    const cleaned = {
      ...(a.street ? { street: a.street.trim() } : {}),
      ...(a.city ? { city: a.city.trim() } : {}),
      ...(a.state ? { state: a.state.trim() } : {}),
      ...(a.zip ? { zip: a.zip.trim() } : {}),
      ...(a.country ? { country: a.country.trim() } : {}),
    };
    if (Object.keys(cleaned).length > 0) result.address = cleaned;
  }
  return result;
}
