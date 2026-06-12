import { HttpInterceptorFn } from '@angular/common/http';

function readCookie(name: string): string | null {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

export const xsrfAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const isRefreshOrLogout =
    request.url.includes('/api/auth/refresh') || request.url.includes('/api/auth/logout');

  if (!isRefreshOrLogout) {
    return next(request);
  }

  const csrfToken = readCookie('XSRF-TOKEN');
  if (!csrfToken) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: {
        'X-XSRF-TOKEN': csrfToken
      }
    })
  );
};
