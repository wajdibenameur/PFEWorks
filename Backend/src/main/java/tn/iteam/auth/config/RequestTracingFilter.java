package tn.iteam.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestTracingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTracingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("{} {} failed after {}ms", method, uri, duration, ex);
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("{} {} completed with status {} in {}ms", method, uri, response.getStatus(), duration);
            logAuthenticatedRequest(method, uri);
        }
    }

    private void logAuthenticatedRequest(String method, String uri) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return;
        }

        log.debug("Authenticated request processed for user: {} on {} {} with authorities: {}",
                authentication.getName(), method, uri, authentication.getAuthorities());
    }
}

