/**
 * Modulo Onboarding: cuestionario inicial, estado de avance, Pacto de Fase I y las 9
 * grabaciones V90 post-seccion (CLAUDE.MD, docs/MODULO_ONBOARDING.md).
 *
 * <p>Solo el paquete onboarding.api es visible desde otros modulos. ArchitectureTest rompe
 * el build si alguien importa onboarding.domain / onboarding.application desde afuera.
 *
 * <p><b>SIN integracion de IA real en este alcance</b> (decision explicita del encargo):
 * {@code ValidacionIAPort} tiene un adaptador NoOp propio de este modulo — no comparte
 * puerto con {@code evidence} (modulo construido en paralelo, conceptualmente distinto:
 * valida transcripciones/audio V90, no evidencia diaria).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Onboarding")
package com.renaser.os.onboarding;
