package com.renaser.os.rocks.application.ports.in.verdugo;

import java.time.LocalDate;

/**
 * Barrido de las 23:55: cualquier Evento Verdugo disparado en {@code fecha}
 * que siga sin resultado pasa a IGNORADO. Bajo el flujo de creación portado
 * del repo viejo (el cliente siempre crea el evento YA resuelto, ver
 * `RegistrarEventoVerdugoUseCase`) no debería quedar ninguno pendiente hoy —
 * este caso de uso existe para cuando exista un disparador server-side de
 * Verdugo (fuera de alcance de esta tarea, ver RK-6 en `docs/MODULO_ROCKS.md`).
 */
public interface ResolverEventosIgnoradosUseCase {

    void resolverPendientesDe(LocalDate fecha);
}
