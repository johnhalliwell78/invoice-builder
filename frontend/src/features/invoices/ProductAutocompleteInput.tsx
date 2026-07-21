import { useState } from 'react';
import type { UseFormRegisterReturn } from 'react-hook-form';
import { useTranslation } from 'react-i18next';

import { useProductSearch } from '@/hooks/useProducts';
import type { Product } from '@/api/products';
import { Input } from '@/components/ui/input';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';

interface Props {
  registration: UseFormRegisterReturn;
  onPick: (product: Product) => void;
  placeholder?: string;
  invalid?: boolean;
}

/**
 * Description input that suggests catalog products while typing.
 * Picking a suggestion is delegated to the parent so it can fill
 * sibling fields (unit price, tax rate) through the form API.
 */
export function ProductAutocompleteInput({ registration, onPick, placeholder, invalid }: Props) {
  const { t } = useTranslation();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const debounced = useDebouncedValue(query, 300);
  const search = useProductSearch(open ? debounced : '');
  const results = search.data?.content ?? [];

  return (
    <div className="relative">
      <Input
        placeholder={placeholder}
        aria-invalid={invalid}
        aria-expanded={open && results.length > 0}
        autoComplete="off"
        {...registration}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
          void registration.onChange(e);
        }}
        onBlur={(e) => {
          setOpen(false);
          void registration.onBlur(e);
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') setOpen(false);
        }}
      />
      {open && results.length > 0 && (
        <ul
          role="listbox"
          aria-label={t('products.title')}
          className="absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-md border bg-background shadow-md"
        >
          {results.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                role="option"
                aria-selected={false}
                className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-accent"
                // preventDefault keeps the input focused so blur doesn't kill the click
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => {
                  onPick(p);
                  setOpen(false);
                  setQuery('');
                }}
              >
                <span className="truncate">
                  {p.name}
                  {p.category && (
                    <span className="ml-2 text-xs text-muted-foreground">{p.category}</span>
                  )}
                </span>
                <span className="shrink-0 tabular-nums text-muted-foreground">
                  {Number(p.unitPrice).toFixed(2)}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
