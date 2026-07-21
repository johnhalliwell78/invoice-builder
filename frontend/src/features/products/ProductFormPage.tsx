import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';

import { useCreateProduct, useProduct, useUpdateProduct } from '@/hooks/useProducts';
import type { ProductPayload } from '@/api/products';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/PageHeader';
import type { ProblemDetail } from '@/types/api';

const numberString = (max?: number) =>
  z
    .string()
    .refine((s) => s !== '' && !Number.isNaN(Number(s)) && Number(s) >= 0, 'Must be a number ≥ 0')
    .refine((s) => max == null || Number(s) <= max, `Must be ≤ ${max}`);

const schema = z.object({
  name: z.string().min(1).max(255),
  category: z.string().max(100).optional().or(z.literal('')),
  unitPrice: numberString(),
  taxRate: numberString(100),
  description: z.string().max(4000).optional().or(z.literal('')),
  active: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

export default function ProductFormPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id && id !== 'new';
  const productId = isEdit ? id : undefined;

  const { data: existing, isPending: loading } = useProduct(productId);
  const create = useCreateProduct();
  const update = useUpdateProduct(productId ?? '');

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      category: '',
      unitPrice: '0.00',
      taxRate: '0',
      description: '',
      active: true,
    },
  });

  useEffect(() => {
    if (existing) {
      reset({
        name: existing.name,
        category: existing.category ?? '',
        unitPrice: existing.unitPrice,
        taxRate: existing.taxRate,
        description: existing.description ?? '',
        active: existing.active,
      });
    }
  }, [existing, reset]);

  async function onSubmit(values: FormValues) {
    const payload: ProductPayload = {
      name: values.name.trim(),
      unitPrice: values.unitPrice,
      taxRate: values.taxRate,
      category: values.category?.trim() || undefined,
      description: values.description?.trim() || undefined,
      active: values.active,
    };
    try {
      if (isEdit && productId) {
        await update.mutateAsync(payload);
        toast.success(t('products.updated'));
      } else {
        await create.mutateAsync(payload);
        toast.success(t('products.created'));
      }
      navigate('/products');
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
        title={isEdit ? t('products.editTitle') : t('products.createTitle')}
        actions={
          <Button variant="outline" onClick={() => navigate('/products')}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            {t('common.back')}
          </Button>
        }
      />

      <form onSubmit={(e) => void handleSubmit(onSubmit)(e)} className="grid max-w-3xl gap-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>{t('products.section.details')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <Field label={t('products.fields.name') + ' *'} error={errors.name?.message}>
              <Input aria-invalid={!!errors.name} {...register('name')} />
            </Field>
            <Field label={t('products.fields.category')}>
              <Input {...register('category')} />
            </Field>
            <Field label={t('products.fields.unitPrice') + ' *'} error={errors.unitPrice?.message}>
              <Input inputMode="decimal" aria-invalid={!!errors.unitPrice} {...register('unitPrice')} />
            </Field>
            <Field label={t('products.fields.taxRate')} error={errors.taxRate?.message}>
              <Input inputMode="decimal" aria-invalid={!!errors.taxRate} {...register('taxRate')} />
            </Field>
            <Field label={t('products.fields.description')} className="sm:col-span-2">
              <Textarea rows={3} {...register('description')} />
            </Field>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" className="h-4 w-4 rounded border" {...register('active')} />
              {t('products.fields.active')}
            </label>
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => navigate('/products')}>
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
