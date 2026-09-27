package com.renaser.os.calendar.application.ports.out.celula;

import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Audiencia CELULA — query nativa PROPIA sobre {@code participantes_programa}/{@code
 * celulas}, sin importar nada de `community` (que es quien construye ese modulo en
 * paralelo — CLAUDE.MD, encargo de este modulo).
 */
public interface ConsultarMiembrosCelulaPort {

    /** Aprendices activos de la celula + el MENTOR que la lidera — findActiveMembersInCell()
     * del repo viejo: quien dirige la sesion tambien recibe su propio aviso.
     *
     * <p><b>Corregido 2026-09-27 (E-363).</b> Tambien cuentan las asignaciones vigentes del grupo
     * ({@code asignaciones_celula}): los aprendices sumados a este grupo sin ser el principal (D-139) y quien
     * lo acompaña aunque lidere varios (D-141). Antes solo contaba {@code participantes_programa.celula_id}. */
    List<UserId> miembrosActivos(UUID celulaId);
}
