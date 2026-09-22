import { HttpClient, HttpHeaders, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map } from 'rxjs';

export interface Account {
  accountNumber: string;
  ownerName: string;
  currency: string;
  balance: number;
  status: string;
}

export interface Movement {
  transferId: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: number;
  balanceAfter: number;
  createdAt: string;
}

export interface Transfer {
  id: string;
  status: string;
  sourceAccount: string;
  targetAccount: string;
  amount: number;
  currency: string;
  description: string | null;
  createdAt: string;
}

export interface TransferRequest {
  sourceAccount: string;
  targetAccount: string;
  amount: number;
  currency: string;
  description: string;
}

/** Error en formato RFC 9457 que devuelve la API. */
export interface Problem {
  title: string;
  detail: string;
  status: number;
  code?: string;
  errors?: Record<string, string>;
}

/**
 * El token vive solo en memoria (una señal), no en localStorage: si una dependencia maliciosa
 * ejecuta JavaScript en la página (XSS), no encuentra un token persistido que robar.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  readonly token = signal<string | null>(null);
  readonly username = signal<string | null>(null);
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(Session).token();
  return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);
};

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  demoToken(username: string): Observable<string> {
    return this.http
      .post<{ access_token: string }>('/api/v1/auth/demo-token', { username })
      .pipe(map((r) => r.access_token));
  }

  accounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/v1/accounts');
  }

  movements(accountNumber: string): Observable<Movement[]> {
    return this.http.get<Movement[]>(`/api/v1/accounts/${encodeURIComponent(accountNumber)}/movements`);
  }

  /** Devuelve la transferencia y si fue una repetición (header Idempotent-Replayed). */
  transfer(body: TransferRequest, idempotencyKey: string): Observable<{ transfer: Transfer; replayed: boolean }> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http
      .post<Transfer>('/api/v1/transfers', body, { headers, observe: 'response' })
      .pipe(map((r) => ({ transfer: r.body as Transfer, replayed: r.headers.get('Idempotent-Replayed') === 'true' })));
  }
}
