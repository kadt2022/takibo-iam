package com.takibo.securitycontext.spi;

import com.takibo.securitycontext.model.TakiboSecurityContext;

public interface TakiboSecurityContextCarrier {
    TakiboSecurityContext getSecurityContext();
}
