import { useId, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';

import { useCustomerSearchInfinite } from '@/hooks/useCustomers';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import type { Customer } from '@/types/api';

interface Props {
  selectedId: string;
  /** Display label for the current selection (the id alone can't be rendered). */
  selectedName?: string;
  onSelect: (customer: Customer | null) => void;
  invalid?: boolean;
  placeholder?: string;
  clearable?: boolean;
}

/**
 * Async customer picker: debounced server-side search, browse-on-focus,
 * keyboard navigation, and paged "load more" — replaces the capped <select>.
 */
export function CustomerCombobox({
  selectedId,
  selectedName,
  onSelect,
  invalid,
  placeholder,
  clearable,
}: Props) {
  const { t } = useTranslation();
  // null = not editing → the input mirrors the current selection's name
  const [text, setText] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listboxId = useId();

  const query = useDebouncedValue(text ?? '', 300);
  const search = useCustomerSearchInfinite(query, open);
  const options = useMemo(
    () => search.data?.pages.flatMap((p) => p.content) ?? [],
    [search.data],
  );

  function pick(customer: Customer | null) {
    onSelect(customer);
    setText(null);
    setOpen(false);
    setHighlight(0);
  }

  function moveHighlight(delta: 1 | -1) {
    setHighlight((h) => {
      const next = Math.min(Math.max(h + delta, 0), Math.max(options.length - 1, 0));
      // Reaching the end pulls in the next page so every match stays
      // keyboard-reachable, not just the first 20.
      if (delta === 1 && next === options.length - 1 && search.hasNextPage && !search.isFetchingNextPage) {
        void search.fetchNextPage();
      }
      return next;
    });
  }

  return (
    <div className="relative">
      <div className="relative">
        <Input
          ref={inputRef}
          role="combobox"
          aria-expanded={open}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-activedescendant={
            open && options[highlight] ? `${listboxId}-opt-${highlight}` : undefined
          }
          aria-invalid={invalid}
          autoComplete="off"
          placeholder={placeholder}
          value={text ?? selectedName ?? ''}
          onFocus={() => setOpen(true)}
          onChange={(e) => {
            setText(e.target.value);
            setOpen(true);
            setHighlight(0);
          }}
          onBlur={() => {
            setOpen(false);
            setText(null);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') {
              setOpen(false);
              setText(null);
            } else if (e.key === 'ArrowDown') {
              e.preventDefault();
              setOpen(true);
              moveHighlight(1);
            } else if (e.key === 'ArrowUp') {
              e.preventDefault();
              moveHighlight(-1);
            } else if (e.key === 'Enter' && open) {
              // Always swallow Enter while the list is open — never submit the form mid-search.
              e.preventDefault();
              if (options[highlight]) pick(options[highlight]);
            }
          }}
        />
        {clearable && selectedId && (
          <button
            type="button"
            aria-label={t('common.clear')}
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground hover:text-foreground"
            onClick={() => {
              pick(null);
              // The button unmounts with the selection — hand focus back to the input.
              inputRef.current?.focus();
            }}
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>

      {open && (
        <div className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-md border bg-background shadow-md">
          {options.length === 0 && !search.isPending && (
            <div className="px-3 py-2 text-sm text-muted-foreground">{t('common.noResults')}</div>
          )}
          <ul id={listboxId} role="listbox" aria-label={t('customers.title')}>
            {options.map((customer, index) => (
              <li
                key={customer.id}
                id={`${listboxId}-opt-${index}`}
                role="option"
                aria-selected={customer.id === selectedId}
                className={`flex w-full cursor-pointer items-center justify-between gap-2 px-3 py-2 text-left text-sm ${
                  index === highlight ? 'bg-accent' : 'hover:bg-accent'
                }`}
                // preventDefault keeps focus on the input so blur doesn't swallow the click
                onMouseDown={(e) => e.preventDefault()}
                onMouseEnter={() => setHighlight(index)}
                onClick={() => pick(customer)}
              >
                <span className="truncate">{customer.name}</span>
                {customer.company && (
                  <span className="shrink-0 text-xs text-muted-foreground">{customer.company}</span>
                )}
              </li>
            ))}
          </ul>
          {search.hasNextPage && (
            <div className="border-t p-1">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="w-full"
                disabled={search.isFetchingNextPage}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => void search.fetchNextPage()}
              >
                {t('common.loadMore')}
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
