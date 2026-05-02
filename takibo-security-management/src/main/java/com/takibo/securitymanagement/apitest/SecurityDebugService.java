package com.takibo.securitymanagement.apitest;

import com.takibo.securitycontext.model.TakiboSecurityContext;
import com.takibo.securitycontext.spi.TakiboSecurityContextCarrier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SecurityDebugService {

    public record Extra(String key, Object value) {}

    public Map<String, Object> snapshot(Authentication authentication, String required, Extra extra) {
        TakiboSecurityContext ctx = extractContext(authentication);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("required", required);

        out.put("authenticated", authentication != null && authentication.isAuthenticated());
        out.put("principal", authentication != null ? authentication.getName() : null);
        out.put("authType", authentication != null ? authentication.getClass().getName() : null);
        out.put("authorities", authentication != null ? authorities(authentication) : List.of());

        out.put("takiboContextPresent", ctx != null);
        out.put("subjectId", ctx != null && ctx.subject() != null ? safe(ctx.subject().subjectId()) : null);
        out.put("organizationId", ctx != null && ctx.tenant() != null ? safe(ctx.tenant().organizationId()) : null);
        out.put("spaceId", ctx != null && ctx.tenant() != null ? safe(ctx.tenant().spaceId()) : null);
        out.put("roles", ctx != null && ctx.subject() != null ? ctx.subject().declaredRoles() : null);

        out.put("accountIdNote", "Not available in ActorIdentity v1 (only subjectId/nature/roles/authMethod)");

        if (extra != null && extra.key() != null) {
            out.put(extra.key(), extra.value());
        }

        return compact(out);
    }

    private TakiboSecurityContext extractContext(Authentication authentication) {
        if (authentication instanceof TakiboSecurityContextCarrier carrier) {
            return carrier.getSecurityContext();
        }
        return null;
    }

    private List<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private Map<String, Object> compact(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            if (e.getValue() != null) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return Map.copyOf(out);
    }
}
