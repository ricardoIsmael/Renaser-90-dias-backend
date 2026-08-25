package com.renaser.os.onboarding.infrastructure.adapter.out.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita {@code @Async} para {@link DespacharValidacionV90Adapter}. Con
 * {@code spring.threads.virtual.enabled=true} (application.yaml), el executor por defecto
 * de Spring Boot corre cada tarea en su propio hilo virtual — encaja con CLAUDE.MD §7
 * ("no bloquear el hilo de request esperando a Gemini").
 */
@Configuration
@EnableAsync
class OnboardingAsyncConfig {
}
