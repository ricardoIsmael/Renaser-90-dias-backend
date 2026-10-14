/**
 * Control de la plataforma (spec 002, fase 1; D-267): el catálogo de funcionalidades, sus interruptores por rol, la
 * auditoría append-only de cada cambio y la consola del rol Soporte TI.
 *
 * <p><b>Nadie depende de este módulo salvo por su {@code api/}</b> (los eventos que escucha {@code notifications}).
 * Lo que usan los demás —la anotación {@code @Funcionalidad}, el puerto {@code Interruptores} y la excepción
 * {@code FuncionalidadApagadaException}— vive en {@code shared}, y este módulo lo implementa: si cada módulo importara
 * {@code plataforma.api}, {@code users -> plataforma -> users} sería un ciclo (plan.md D1).
 *
 * <p><b>Cómo rige un cambio en menos de un minuto sin reiniciar:</b> el estado vive en Postgres (solo los desvíos del
 * catálogo), cada instancia tiene una copia en memoria con su versión, y un cambio avisa por el canal Redis
 * {@code renaser:funcionalidades}; de respaldo, la copia se recarga cada 30 s (plan.md D3).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Plataforma")
package com.renaser.os.plataforma;
