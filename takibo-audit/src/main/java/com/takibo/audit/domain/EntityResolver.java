// com.takibo.audit.domain.EntityResolver  (adapter)
package com.takibo.audit.domain;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntityResolver {
    private final EntityIdResolver delegate;   // <- la nouvelle façade

    public String resolveId(JoinPoint jp, String expression, Object result) {
        return delegate.resolveId(jp, expression, result);
    }
}
