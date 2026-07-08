import { z } from 'zod';

const emailSchema = z.string().email();

/**
 * Splits a comma/semicolon-separated input into trimmed, case-insensitively
 * deduplicated email strings (first casing wins). Does NOT validate — pair
 * with findInvalidEmail.
 */
export function parseEmailList(input: string): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of input.split(/[,;]/)) {
    const email = raw.trim();
    if (!email) continue;
    const key = email.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(email);
  }
  return out;
}

/** Returns the first syntactically invalid address, or null if all pass. */
export function findInvalidEmail(emails: string[]): string | null {
  for (const email of emails) {
    if (!emailSchema.safeParse(email).success) return email;
  }
  return null;
}
