import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AUTH_CONTEXT } from '../auth/auth-context.port';

export const authHeaderInterceptor: HttpInterceptorFn = (request, next) => {
  const authContext = inject(AUTH_CONTEXT);
  const token = authContext.getAccessToken();

  // Skip adding token for login endpoint
  if (!token || request.url.includes('/api/auth/login') || request.url.includes('/api/auth/refresh')) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    })
  );
};

