import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import { StompClientService } from '../../../core/realtime/stomp-client.service';

@Component({
  selector: 'app-login-page',
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <main class="login-shell">
      <section class="login-layout">
        <article class="visual-panel">
          <div class="visual-overlay"></div>
          <img src="/smu.png" alt="South Mediterranean University" class="campus-image" />
          <div class="visual-copy">
            <p class="eyebrow">MonitorFlow Platform</p>
            <h1>South Mediterranean University</h1>
            <p>
               Enterprise workspace for secure monitoring, ticketing, and infrastructure operations.</p>
          </div>
        </article>

        <section class="form-panel">
          <div class="form-intro">
            <p class="eyebrow">Welcome Back</p>
            <h2>Sign in to continue</h2>
            <p>Unified platform for infrastructure monitoring, incident management, and system administration.
Sign in using administrator-managed credentials.</p>
          </div>

          <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="login-form">
            <label class="field">
              <span>Username</span>
              <input
                id="username"
                type="text"
                formControlName="username"
                placeholder="Enter your username"
              />
            </label>

            <label class="field">
              <span>Password</span>
              <input
                id="password"
                type="password"
                formControlName="password"
                placeholder="Enter your password"
              />
            </label>

            <button type="submit" [disabled]="loginForm.invalid || isLoading()" class="login-btn">
              {{ isLoading() ? 'Signing in...' : 'Login' }}
            </button>

            @if (errorMessage()) {
              <div class="error-message">{{ errorMessage() }}</div>
            }
          </form>
        </section>
      </section>
    </main>
  `,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      background:
        radial-gradient(circle at top left, rgba(14, 165, 233, 0.24), transparent 30%),
        radial-gradient(circle at bottom right, rgba(15, 118, 110, 0.18), transparent 26%),
        linear-gradient(135deg, #eff6ff 0%, #f8fafc 52%, #e2e8f0 100%);
      color: #0f172a;
      font-family: "Segoe UI", "Helvetica Neue", sans-serif;
    }

    .login-shell {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 2rem;
    }

    .login-layout {
      width: min(1180px, 100%);
      min-height: 680px;
      display: grid;
      grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.8fr);
      border-radius: 2rem;
      overflow: hidden;
      background: rgba(255, 255, 255, 0.8);
      border: 1px solid rgba(148, 163, 184, 0.25);
      box-shadow: 0 30px 80px rgba(15, 23, 42, 0.16);
      backdrop-filter: blur(18px);
    }

    .visual-panel {
      position: relative;
      min-height: 680px;
      overflow: hidden;
      background: #0f172a;
    }

    .campus-image {
      width: 100%;
      height: 100%;
      object-fit: cover;
      object-position: center;
      display: block;
      transform: scale(1.02);
    }

    .visual-overlay {
      position: absolute;
      inset: 0;
      background:
        linear-gradient(180deg, rgba(15, 23, 42, 0.18), rgba(15, 23, 42, 0.68)),
        linear-gradient(135deg, rgba(14, 165, 233, 0.2), transparent 40%);
      z-index: 1;
    }

    .visual-copy {
      position: absolute;
      left: 2.5rem;
      right: 2.5rem;
      bottom: 2.5rem;
      z-index: 2;
      color: #f8fafc;
      display: grid;
      gap: 0.75rem;
    }

    .visual-copy h1,
    .form-intro h2 {
      margin: 0;
      line-height: 1.05;
    }

    .visual-copy h1 {
      font-size: clamp(2.3rem, 4vw, 3.6rem);
      max-width: 12ch;
    }

    .visual-copy p:last-child,
    .form-intro p:last-child {
      margin: 0;
      font-size: 1rem;
      line-height: 1.7;
    }

    .eyebrow {
      margin: 0;
      text-transform: uppercase;
      letter-spacing: 0.18em;
      font-size: 0.76rem;
      font-weight: 700;
    }

    .form-panel {
      display: grid;
      align-content: center;
      gap: 2rem;
      padding: 3.5rem 3rem;
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.98));
    }

    .form-intro {
      display: grid;
      gap: 0.85rem;
    }

    .form-intro .eyebrow {
      color: #0f766e;
    }

    .form-intro h2 {
      font-size: clamp(2rem, 3vw, 2.8rem);
      color: #0f172a;
    }

    .form-intro p:last-child {
      color: #475569;
      max-width: 34ch;
    }

    .login-form {
      display: grid;
      gap: 1.25rem;
    }

    .field {
      display: grid;
      gap: 0.55rem;
    }

    .field span {
      font-size: 0.92rem;
      font-weight: 700;
      color: #334155;
    }

    .field input {
      width: 100%;
      border-radius: 1rem;
      border: 1px solid rgba(148, 163, 184, 0.45);
      background: rgba(255, 255, 255, 0.92);
      padding: 0.95rem 1rem;
      font-size: 1rem;
      color: #0f172a;
      transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
      box-sizing: border-box;
    }

    .field input:focus {
      outline: none;
      border-color: #0f766e;
      box-shadow: 0 0 0 4px rgba(15, 118, 110, 0.14);
      transform: translateY(-1px);
    }

    .login-btn {
      margin-top: 0.5rem;
      border: none;
      border-radius: 999px;
      padding: 1rem 1.2rem;
      background: linear-gradient(135deg, #0f766e, #0ea5e9);
      color: #fff;
      font-size: 1rem;
      font-weight: 700;
      cursor: pointer;
      box-shadow: 0 18px 32px rgba(14, 165, 233, 0.22);
      transition: transform 160ms ease, box-shadow 160ms ease, opacity 160ms ease;
    }

    .login-btn:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 22px 36px rgba(14, 165, 233, 0.28);
    }

    .login-btn:disabled {
      cursor: not-allowed;
      opacity: 0.55;
      box-shadow: none;
    }

    .error-message {
      border-radius: 1rem;
      padding: 0.9rem 1rem;
      border: 1px solid rgba(220, 38, 38, 0.16);
      background: rgba(254, 242, 242, 0.94);
      color: #991b1b;
      font-size: 0.95rem;
      text-align: center;
    }

    @media (max-width: 980px) {
      .login-layout {
        grid-template-columns: 1fr;
        min-height: auto;
      }

      .visual-panel {
        min-height: 320px;
      }

      .form-panel {
        padding: 2rem 1.5rem;
      }
    }

    @media (max-width: 640px) {
      .login-shell {
        padding: 1rem;
      }

      .visual-copy {
        left: 1.25rem;
        right: 1.25rem;
        bottom: 1.25rem;
      }

      .visual-panel {
        min-height: 260px;
      }
    }
  `]
})
export class LoginPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly authContext = inject(AUTH_CONTEXT);
  private readonly stompClient = inject(StompClientService);
  private readonly config = inject<AppConfig>(APP_CONFIG);

  readonly loginForm = this.fb.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  onSubmit() {
    if (this.loginForm.invalid) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const username = this.loginForm.controls.username.value ?? '';
    const password = this.loginForm.controls.password.value ?? '';

    this.http.post<{ access_token?: string }>(
      `${this.config.authApiUrl}/api/auth/login`,
      { username, password },
      { withCredentials: true }
    ).subscribe({
      next: (response) => {
        const accessToken = response?.access_token;
        if (!accessToken) {
          this.errorMessage.set('Login failed: access token missing.');
          this.isLoading.set(false);
          return;
        }

        this.authContext.setTokens(accessToken, null);
        this.stompClient.connect();
        this.isLoading.set(false);
        void this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.errorMessage.set('Username or password is incorrect.');
        this.isLoading.set(false);
      }
    });
  }
}

