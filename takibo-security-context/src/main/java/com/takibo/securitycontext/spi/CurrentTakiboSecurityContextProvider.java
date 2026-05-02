package com.takibo.securitycontext.spi;

import com.takibo.securitycontext.model.TakiboSecurityContext;

public interface CurrentTakiboSecurityContextProvider {
    TakiboSecurityContext current();
}
