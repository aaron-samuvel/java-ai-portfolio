package com.aaron.helpdesk.support;

import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Resolves customer identity exclusively from the authenticated security principal. */
@Component
public class DemoAccount {
    public String customerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"))) {
            throw new AccessDeniedException("An authenticated customer account is required");
        }
        return authentication.getName();
    }

    public String memoryId() { return customerId(); }
}
