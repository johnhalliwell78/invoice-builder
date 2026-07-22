import { createBrowserRouter, Navigate } from 'react-router-dom';
import { lazy, Suspense } from 'react';

import { ProtectedRoute } from '@/components/ProtectedRoute';
import { AnonymousRoute } from '@/components/AnonymousRoute';
import { AppShell } from '@/components/layout/AppShell';

const LoginPage = lazy(() => import('@/features/auth/LoginPage'));
const RegisterPage = lazy(() => import('@/features/auth/RegisterPage'));
const OAuth2CallbackPage = lazy(() => import('@/features/auth/OAuth2CallbackPage'));
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage'));
const CustomerListPage = lazy(() => import('@/features/customers/CustomerListPage'));
const CustomerFormPage = lazy(() => import('@/features/customers/CustomerFormPage'));
const ProductListPage = lazy(() => import('@/features/products/ProductListPage'));
const ProductFormPage = lazy(() => import('@/features/products/ProductFormPage'));
const InvoiceListPage = lazy(() => import('@/features/invoices/InvoiceListPage'));
const InvoiceFormPage = lazy(() => import('@/features/invoices/InvoiceFormPage'));
const InvoiceDetailPage = lazy(() => import('@/features/invoices/InvoiceDetailPage'));
const PublicInvoicePage = lazy(() => import('@/features/public/PublicInvoicePage'));
const SettingsPage = lazy(() => import('@/features/settings/SettingsPage'));
const InviteAcceptPage = lazy(() => import('@/features/auth/InviteAcceptPage'));

const Fallback = () => (
  <div className="flex min-h-screen items-center justify-center text-muted-foreground">Loading…</div>
);

export const router = createBrowserRouter([
  {
    element: <AnonymousRoute />,
    children: [
      {
        path: '/login',
        element: (
          <Suspense fallback={<Fallback />}>
            <LoginPage />
          </Suspense>
        ),
      },
      {
        path: '/register',
        element: (
          <Suspense fallback={<Fallback />}>
            <RegisterPage />
          </Suspense>
        ),
      },
    ],
  },
  {
    path: '/auth/oauth2/callback',
    element: (
      <Suspense fallback={<Fallback />}>
        <OAuth2CallbackPage />
      </Suspense>
    ),
  },
  {
    path: '/i/:token',
    element: (
      <Suspense fallback={<Fallback />}>
        <PublicInvoicePage />
      </Suspense>
    ),
  },
  {
    path: '/invite/:token',
    element: (
      <Suspense fallback={<Fallback />}>
        <InviteAcceptPage />
      </Suspense>
    ),
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppShell />,
        children: [
          {
            index: true,
            element: (
              <Suspense fallback={<Fallback />}>
                <DashboardPage />
              </Suspense>
            ),
          },
          {
            path: 'invoices',
            element: (
              <Suspense fallback={<Fallback />}>
                <InvoiceListPage />
              </Suspense>
            ),
          },
          {
            path: 'invoices/new',
            element: (
              <Suspense fallback={<Fallback />}>
                <InvoiceFormPage />
              </Suspense>
            ),
          },
          {
            path: 'invoices/:id',
            element: (
              <Suspense fallback={<Fallback />}>
                <InvoiceDetailPage />
              </Suspense>
            ),
          },
          {
            path: 'invoices/:id/edit',
            element: (
              <Suspense fallback={<Fallback />}>
                <InvoiceFormPage />
              </Suspense>
            ),
          },
          {
            path: 'customers',
            element: (
              <Suspense fallback={<Fallback />}>
                <CustomerListPage />
              </Suspense>
            ),
          },
          {
            path: 'customers/new',
            element: (
              <Suspense fallback={<Fallback />}>
                <CustomerFormPage />
              </Suspense>
            ),
          },
          {
            path: 'customers/:id',
            element: (
              <Suspense fallback={<Fallback />}>
                <CustomerFormPage />
              </Suspense>
            ),
          },
          {
            path: 'estimates',
            element: (
              <Suspense fallback={<Fallback />}>
                <InvoiceListPage docType="ESTIMATE" />
              </Suspense>
            ),
          },
          {
            path: 'estimates/new',
            element: (
              <Suspense fallback={<Fallback />}>
                <InvoiceFormPage docType="ESTIMATE" />
              </Suspense>
            ),
          },
          {
            path: 'estimates/:id',
            element: (
              <Suspense fallback={<Fallback />}>
                <InvoiceDetailPage />
              </Suspense>
            ),
          },
          {
            path: 'estimates/:id/edit',
            element: (
              <Suspense fallback={<Fallback />}>
                <InvoiceFormPage docType="ESTIMATE" />
              </Suspense>
            ),
          },
          {
            path: 'products',
            element: (
              <Suspense fallback={<Fallback />}>
                <ProductListPage />
              </Suspense>
            ),
          },
          {
            path: 'products/new',
            element: (
              <Suspense fallback={<Fallback />}>
                <ProductFormPage />
              </Suspense>
            ),
          },
          {
            path: 'products/:id',
            element: (
              <Suspense fallback={<Fallback />}>
                <ProductFormPage />
              </Suspense>
            ),
          },
          {
            path: 'settings',
            element: (
              <Suspense fallback={<Fallback />}>
                <SettingsPage />
              </Suspense>
            ),
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
]);
