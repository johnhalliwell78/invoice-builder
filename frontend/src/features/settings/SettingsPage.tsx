import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Trash2, Upload } from 'lucide-react';

import {
  useDeleteLogo,
  useTenant,
  useTenantLogo,
  useUpdateTenant,
  useUploadLogo,
} from '@/hooks/useTenant';
import type { TenantUpdatePayload } from '@/api/tenant';
import { useAuthStore } from '@/store/authStore';
import { TeamCard } from './TeamCard';
import { SUPPORTED_LOCALES } from '@/i18n';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/PageHeader';
import type { ProblemDetail } from '@/types/api';

const DEFAULT_ACCENT = '#2563EB';

const schema = z.object({
  name: z.string().min(1).max(255),
  defaultCurrency: z.string().length(3),
  defaultLocale: z.enum(SUPPORTED_LOCALES),
  taxId: z.string().max(100).optional().or(z.literal('')),
  invoicePrefix: z
    .string()
    .regex(/^[A-Za-z0-9-]{1,10}$/, 'invalid')
    .optional()
    .or(z.literal('')),
  defaultTemplate: z.enum(['classic', 'modern']),
  brandingColor: z.string().regex(/^#[0-9A-Fa-f]{6}$/),
  footerText: z.string().max(2000).optional().or(z.literal('')),
  paymentInfo: z.string().max(2000).optional().or(z.literal('')),
  address: z.object({
    street: z.string().max(255).optional().or(z.literal('')),
    city: z.string().max(255).optional().or(z.literal('')),
    state: z.string().max(255).optional().or(z.literal('')),
    zip: z.string().max(20).optional().or(z.literal('')),
    country: z.string().max(100).optional().or(z.literal('')),
  }),
});
type FormValues = z.infer<typeof schema>;

export default function SettingsPage() {
  const { t } = useTranslation();
  const currentUser = useAuthStore((s) => s.user);
  const canEdit = currentUser?.role === 'OWNER' || currentUser?.role === 'ADMIN';
  const { data: tenant, isPending } = useTenant();
  const update = useUpdateTenant();
  const uploadLogo = useUploadLogo();
  const deleteLogo = useDeleteLogo();
  const logo = useTenantLogo(!!tenant?.logoPath);
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      defaultCurrency: 'USD',
      defaultLocale: 'en',
      taxId: '',
      invoicePrefix: 'INV',
      defaultTemplate: 'classic',
      brandingColor: DEFAULT_ACCENT,
      footerText: '',
      paymentInfo: '',
      address: { street: '', city: '', state: '', zip: '', country: '' },
    },
  });

  useEffect(() => {
    if (!tenant) return;
    reset({
      name: tenant.name,
      defaultCurrency: tenant.defaultCurrency,
      defaultLocale: (SUPPORTED_LOCALES as readonly string[]).includes(tenant.defaultLocale)
        ? (tenant.defaultLocale as FormValues['defaultLocale'])
        : 'en',
      taxId: tenant.taxId ?? '',
      invoicePrefix: tenant.invoicePrefix ?? '',
      defaultTemplate: tenant.defaultTemplate === 'modern' ? 'modern' : 'classic',
      brandingColor: tenant.brandingColor ?? DEFAULT_ACCENT,
      footerText: tenant.footerText ?? '',
      paymentInfo: tenant.paymentInfo ?? '',
      address: {
        street: tenant.address?.street ?? '',
        city: tenant.address?.city ?? '',
        state: tenant.address?.state ?? '',
        zip: tenant.address?.zip ?? '',
        country: tenant.address?.country ?? '',
      },
    });
  }, [tenant, reset]);

  useEffect(() => {
    if (!logo.data) {
      setLogoUrl(null);
      return;
    }
    const url = URL.createObjectURL(logo.data);
    setLogoUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [logo.data]);

  function errorToast(err: unknown) {
    const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
    toast.error(detail ?? t('auth.errors.default'));
  }

  async function onSubmit(values: FormValues) {
    const payload: TenantUpdatePayload = {
      name: values.name.trim(),
      defaultCurrency: values.defaultCurrency.toUpperCase(),
      defaultLocale: values.defaultLocale,
      taxId: values.taxId ?? '',
      invoicePrefix: values.invoicePrefix || undefined,
      defaultTemplate: values.defaultTemplate,
      brandingColor: values.brandingColor,
      footerText: values.footerText ?? '',
      paymentInfo: values.paymentInfo ?? '',
      address: values.address,
    };
    try {
      await update.mutateAsync(payload);
      toast.success(t('settings.saved'));
    } catch (err) {
      errorToast(err);
    }
  }

  async function handleLogoSelected(file: File | undefined) {
    if (!file) return;
    try {
      await uploadLogo.mutateAsync(file);
      toast.success(t('settings.logo.uploaded'));
    } catch (err) {
      errorToast(err);
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  async function handleLogoRemove() {
    try {
      await deleteLogo.mutateAsync();
      setLogoUrl(null);
      toast.success(t('settings.logo.removed'));
    } catch (err) {
      errorToast(err);
    }
  }

  if (isPending) {
    return <div className="text-muted-foreground">{t('common.loading')}</div>;
  }

  return (
    <div>
      <PageHeader title={t('settings.title')} description={t('settings.subtitle')} />

      <form
        onSubmit={(e) => void handleSubmit(onSubmit)(e)}
        className="grid max-w-4xl gap-6"
        noValidate
      >
        <fieldset disabled={!canEdit} className="contents">
        <Card>
          <CardHeader>
            <CardTitle>{t('settings.section.company')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5 sm:col-span-2">
              <Label htmlFor="set-name">{t('settings.fields.name')} *</Label>
              <Input id="set-name" aria-invalid={!!errors.name} {...register('name')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="set-currency">{t('settings.fields.currency')}</Label>
              <Input id="set-currency" maxLength={3} aria-invalid={!!errors.defaultCurrency} {...register('defaultCurrency')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="set-locale">{t('settings.fields.locale')}</Label>
              <select
                id="set-locale"
                className="h-10 w-full rounded-md border bg-background px-3 text-sm"
                {...register('defaultLocale')}
              >
                {SUPPORTED_LOCALES.map((loc) => (
                  <option key={loc} value={loc}>
                    {loc.toUpperCase()}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="set-taxid">{t('settings.fields.taxId')}</Label>
              <Input id="set-taxid" {...register('taxId')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="set-prefix">{t('settings.fields.invoicePrefix')}</Label>
              <Input id="set-prefix" aria-invalid={!!errors.invoicePrefix} {...register('invoicePrefix')} />
              <p className="text-xs text-muted-foreground">{t('settings.fields.invoicePrefixHint')}</p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('settings.section.address')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5 sm:col-span-2">
              <Label htmlFor="set-street">{t('settings.fields.street')}</Label>
              <Input id="set-street" {...register('address.street')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="set-zip">{t('settings.fields.zip')}</Label>
              <Input id="set-zip" {...register('address.zip')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="set-city">{t('settings.fields.city')}</Label>
              <Input id="set-city" {...register('address.city')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="set-state">{t('settings.fields.state')}</Label>
              <Input id="set-state" {...register('address.state')} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="set-country">{t('settings.fields.country')}</Label>
              <Input id="set-country" {...register('address.country')} />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('settings.section.branding')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="set-template">{t('settings.fields.defaultTemplate')}</Label>
              <select
                id="set-template"
                className="h-10 w-full rounded-md border bg-background px-3 text-sm"
                {...register('defaultTemplate')}
              >
                <option value="classic">{t('invoices.template.classic')}</option>
                <option value="modern">{t('invoices.template.modern')}</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="set-color">{t('settings.fields.brandingColor')}</Label>
              <Input id="set-color" type="color" className="h-10 w-20 p-1" {...register('brandingColor')} />
              <p className="text-xs text-muted-foreground">{t('settings.fields.brandingColorHint')}</p>
            </div>
            <div className="space-y-1.5 sm:col-span-2">
              <Label htmlFor="set-footer">{t('settings.fields.footerText')}</Label>
              <Textarea id="set-footer" rows={2} {...register('footerText')} />
              <p className="text-xs text-muted-foreground">{t('settings.fields.footerTextHint')}</p>
            </div>
            <div className="space-y-1.5 sm:col-span-2">
              <Label htmlFor="set-payment">{t('settings.fields.paymentInfo')}</Label>
              <Textarea id="set-payment" rows={3} {...register('paymentInfo')} />
              <p className="text-xs text-muted-foreground">{t('settings.fields.paymentInfoHint')}</p>
            </div>
          </CardContent>
        </Card>

        <div className="flex justify-end">
          <Button type="submit" disabled={isSubmitting || !canEdit}>
            {isSubmitting ? t('common.saving') : t('common.save')}
          </Button>
        </div>
        </fieldset>
      </form>

      <Card className="mt-6 max-w-4xl">
        <CardHeader>
          <CardTitle>{t('settings.section.logo')}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center gap-4">
          {logoUrl ? (
            <img src={logoUrl} alt={t('settings.section.logo')} className="max-h-16 max-w-48 rounded border p-1" />
          ) : (
            <p className="text-sm text-muted-foreground">{t('settings.logo.none')}</p>
          )}
          <input
            ref={fileInputRef}
            type="file"
            accept="image/png,image/jpeg"
            className="hidden"
            aria-label={t('settings.logo.upload')}
            onChange={(e) => void handleLogoSelected(e.target.files?.[0])}
          />
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={uploadLogo.isPending || !canEdit}
              onClick={() => fileInputRef.current?.click()}
            >
              <Upload className="mr-2 h-4 w-4" />
              {t('settings.logo.upload')}
            </Button>
            {tenant?.logoPath && (
              <Button
                type="button"
                variant="ghost"
                disabled={deleteLogo.isPending || !canEdit}
                onClick={() => void handleLogoRemove()}
              >
                <Trash2 className="mr-2 h-4 w-4 text-destructive" />
                {t('settings.logo.remove')}
              </Button>
            )}
          </div>
          <p className="w-full text-xs text-muted-foreground">{t('settings.logo.hint')}</p>
        </CardContent>
      </Card>

      {canEdit && <TeamCard />}
    </div>
  );
}
