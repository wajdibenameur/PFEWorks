import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { APP_CONFIG, AppConfig } from '../../../core/config/app-config.token';
import { AUTH_CONTEXT } from '../../../core/auth/auth-context.port';

@Component({
  selector: 'app-login-page',
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="login-container">
      <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="login-form">
        <h2>Login</h2>

        <div class="form-group">
          <label for="username">Username</label>
          <input
            id="username"
            type="text"
            formControlName="username"
            placeholder="Enter username"
          />
        </div>

        <div class="form-group">
          <label for="password">Password</label>
          <input
            id="password"
            type="password"
            formControlName="password"
            placeholder="Enter password"
          />
        </div>

        <button type="submit" [disabled]="loginForm.invalid || isLoading()" class="login-btn">
          {{ isLoading() ? 'Logging in...' : 'Login' }}
        </button>

        @if (errorMessage()) {
          <div class="error-message">{{ errorMessage() }}</div>
        }
      </form>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 100vh;
      background-color: #f5f5f5;
    }
    .login-form {
      background: white;
      padding: 2rem;
      border-radius: 8px;
      box-shadow: 0 2px 10px rgba(0,0,0,0.1);
      width: 100%;
      max-width: 400px;
    }
    .form-group {
      margin-bottom: 1rem;
    }
    label {
      display: block;
      margin-bottom: 0.5rem;
      font-weight: 500;
    }
    input {
      width: 100%;
      padding: 0.75rem;
      border: 1px solid #ddd;
      border-radius: 4px;
      font-size: 1rem;
    }
    .login-btn {
      width: 100%;
      padding: 0.75rem;
      background-color: #007bff;
      color: white;
      border: none;
      border-radius: 4px;
      font-size: 1rem;
      cursor: pointer;
    }
    .login-btn:disabled {
      background-color: #ccc;
      cursor: not-allowed;
    }
    .error-message {
      color: #dc3545;
      margin-top: 1rem;
      text-align: center;
    }
  `]
})
export class LoginPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly config = inject<AppConfig>(APP_CONFIG);
  private readonly authContext = inject(AUTH_CONTEXT);

  readonly loginForm = this.fb.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  onSubmit() {
    if (this.loginForm.invalid) return;

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { username, password } = this.loginForm.value;

    this.http.post(`${this.config.authApiUrl}/api/auth/login`, { username, password })
      .subscribe({
        next: (response: any) => {
          this.authContext.setTokens(response.access_token, response.refresh_token ?? null);

          // Redirect based on role
          const roles = this.extractRolesFromToken(response.access_token);
          this.redirectBasedOnRole(roles);
        },
        error: (error) => {
          this.isLoading.set(false);
          this.errorMessage.set(error.error?.message || 'Login failed');
        }
      });
  }

  private extractRolesFromToken(token: string): string[] {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return ((payload.realm_access?.roles || []) as string[]).map((role) =>
        String(role).trim().toUpperCase()
      );
    } catch {
      return [];
    }
  }

  private redirectBasedOnRole(roles: string[]): void {
    // UX-only landing choice. Backend authorization remains the source of truth.
    if (roles.includes('SUPERADMIN') || roles.includes('ADMIN')) {
      this.router.navigate(['/admin']);
    } else if (roles.includes('SUPPORT')) {
      this.router.navigate(['/dashboard']);
    } else {
      this.router.navigate(['/dashboard']);
    }
  }
}

