import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { Account, Api, Movement, Problem, Session, Transfer } from './api';

@Component({
  selector: 'app-root',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly api = inject(Api);
  protected readonly session = inject(Session);
  private readonly fb = inject(FormBuilder);

  protected readonly demoUsers = ['ana', 'carlos', 'laura'];
  protected readonly accounts = signal<Account[]>([]);
  protected readonly selected = signal<string | null>(null);
  protected readonly movements = signal<Movement[]>([]);
  protected readonly lastTransfer = signal<{ transfer: Transfer; replayed: boolean } | null>(null);
  protected readonly problem = signal<Problem | null>(null);
  protected readonly sending = signal(false);

  /**
   * Una llave por intención de pago. Si la red falla y el usuario reintenta, se envía la MISMA llave
   * y la API devuelve la transferencia original en vez de cobrar dos veces. Solo cambia tras un éxito.
   */
  protected readonly idempotencyKey = signal(crypto.randomUUID());

  protected readonly selectedAccount = computed(() =>
    this.accounts().find((a) => a.accountNumber === this.selected()),
  );

  protected readonly form = this.fb.nonNullable.group({
    targetAccount: ['', [Validators.required, Validators.pattern(/^\d{10}$/)]],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    description: ['', [Validators.maxLength(140), Validators.pattern(/^[\p{L}\p{N} .,#-]*$/u)]],
  });

  login(username: string): void {
    this.problem.set(null);
    this.api.demoToken(username).subscribe({
      next: (token) => {
        this.session.token.set(token);
        this.session.username.set(username);
        this.refresh();
      },
      error: (e) => this.showError(e),
    });
  }

  logout(): void {
    this.session.token.set(null);
    this.session.username.set(null);
    this.accounts.set([]);
    this.movements.set([]);
    this.selected.set(null);
    this.lastTransfer.set(null);
  }

  select(accountNumber: string): void {
    this.selected.set(accountNumber);
    this.api.movements(accountNumber).subscribe({ next: (m) => this.movements.set(m), error: (e) => this.showError(e) });
  }

  send(): void {
    const source = this.selectedAccount();
    if (!source || this.form.invalid || this.sending()) {
      this.form.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    this.problem.set(null);
    const value = this.form.getRawValue();
    this.api
      .transfer({ sourceAccount: source.accountNumber, currency: source.currency, ...value }, this.idempotencyKey())
      .subscribe({
        next: (result) => {
          this.lastTransfer.set(result);
          this.idempotencyKey.set(crypto.randomUUID());
          this.form.reset();
          this.sending.set(false);
          this.refresh();
        },
        error: (e) => {
          this.sending.set(false);
          this.showError(e);
        },
      });
  }

  private refresh(): void {
    this.api.accounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        const current = this.selected() ?? accounts[0]?.accountNumber;
        if (current) {
          this.select(current);
        }
      },
      error: (e) => this.showError(e),
    });
  }

  private showError(e: unknown): void {
    const body = e instanceof HttpErrorResponse ? (e.error as Problem | null) : null;
    this.problem.set(
      body?.title ? body : { title: 'Sin conexión', detail: 'No se pudo contactar la API. Puedes reintentar.', status: 0 },
    );
  }
}
