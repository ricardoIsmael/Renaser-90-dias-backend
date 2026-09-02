package com.renaser.os.onboarding.infrastructure.adapter.out.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita {@code @Async} para {@link DespacharValidacionV90Adapter}. Con
 * {@code spring.threads.virtual.enabled=true} (application.yaml), el executor por defecto
 * de Spring Boot corre cada tarea en su propio hilo virtual — encaja con CLAUDE.MD §7
 * ("no bloquear el hilo de request esperando a Gemini").
 *
 * <p><b>C-1 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):</b> ese
 * executor por defecto ({@code SimpleAsyncTaskExecutor}, el que Spring Boot arma cuando hay
 * hilos virtuales) NO tiene cola ni límite propio — lanza un hilo virtual por cada
 * despacho, sin tope, salvo que se fije {@code spring.task.execution.simple.concurrency-limit}
 * (application.yaml). Antes de este cambio esa propiedad no estaba fijada: nada impedía que
 * N aprendices pidiendo validación V90 al mismo tiempo dispararan N llamadas concurrentes a
 * Gemini sin ningún tope. No hace falta un {@code @Bean Executor} acá porque
 * {@code concurrency-limit} ya acota el bean por defecto que este {@code @EnableAsync} usa —
 * declarar un executor propio duplicaría esa configuración sin necesidad.
 */
@Configuration
@EnableAsync
class OnboardingAsyncConfig {
}
