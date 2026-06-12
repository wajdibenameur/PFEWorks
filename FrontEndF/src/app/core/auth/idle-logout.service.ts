import { Inject, Injectable } from '@angular/core';
import { Subscription } from 'rxjs';
import { AUTH_CONTEXT, AuthContextPort } from './auth-context.port';

@Injectable({ providedIn: 'root' })
export class IdleLogoutService {
  private static readonly IDLE_TIMEOUT_MS = 5 * 60 * 1000;

  private timerId: ReturnType<typeof setTimeout> | null = null;
  private authSubscription: Subscription | null = null;
  private listenersBound = false;
  private isAuthenticated = false;

  constructor(@Inject(AUTH_CONTEXT) private readonly auth: AuthContextPort) {}

  start(): void {
    if (!this.authSubscription) {
      this.authSubscription = this.auth.isAuthenticated$.subscribe((isAuthenticated) => {
        this.isAuthenticated = isAuthenticated;
        if (!isAuthenticated) {
          this.clearTimer();
          return;
        }
        this.resetTimer();
      });
    }

    if (this.listenersBound) {
      return;
    }

    const activityHandler = () => {
      if (this.isAuthenticated) {
        this.resetTimer();
      }
    };

    window.addEventListener('mousemove', activityHandler, { passive: true });
    window.addEventListener('mousedown', activityHandler, { passive: true });
    window.addEventListener('keydown', activityHandler);
    window.addEventListener('scroll', activityHandler, { passive: true });
    window.addEventListener('touchstart', activityHandler, { passive: true });

    this.listenersBound = true;
  }

  private resetTimer(): void {
    this.clearTimer();
    this.timerId = setTimeout(() => {
      if (!this.isAuthenticated) {
        return;
      }
      this.auth.logout();
    }, IdleLogoutService.IDLE_TIMEOUT_MS);
  }

  private clearTimer(): void {
    if (!this.timerId) {
      return;
    }
    clearTimeout(this.timerId);
    this.timerId = null;
  }
}
