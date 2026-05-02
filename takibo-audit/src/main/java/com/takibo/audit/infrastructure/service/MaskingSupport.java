package com.takibo.audit.infrastructure.service;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Expose le MaskingService sous forme statique pour les usages toString / logs.
 * (Utilisation ponctuelle, acceptable car limitée aux besoins d'observabilité.)
 */
@Component
public class MaskingSupport implements ApplicationContextAware {

    private static MaskingService maskingService;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        maskingService = applicationContext.getBean(MaskingService.class);
    }

    public static MaskingService getMaskingService() {
        return maskingService;
    }
}
