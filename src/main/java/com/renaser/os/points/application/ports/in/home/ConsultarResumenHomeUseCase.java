package com.renaser.os.points.application.ports.in.home;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code GET /api/v1/home} — agregador del resumen del dia para la pantalla de Inicio
 * (gap #21, docs/PLAN_INTEGRACION_FRONTEND.md §5). {@code points} compone lo que HOY tiene
 * contrato publico: puntaje/coherencia/racha son dominio propio; lo que depende de otro
 * modulo se agrega SOLO si ese modulo ya expone un finder en su {@code api} (CLAUDE.MD sec.
 * 4.3/5.1) — hoy ninguno de los 3 candidatos evaluados lo expone:
 *
 * <ul>
 *   <li>{@code habits.api} no tiene un finder de "habitos del dia" (solo
 *       {@code EntradaDiarioFinder}, que es diario, no tracks de habitos).</li>
 *   <li>{@code calendar.api} solo publica un evento de dominio
 *       ({@code RecordatorioEventoDebidoEvent}), no un finder de "proximo evento".</li>
 *   <li>{@code notifications} todavia no declara ningun paquete {@code api}
 *       ({@code @NamedInterface}) — no hay nada que importar.</li>
 * </ul>
 *
 * <p>Ninguno de esos 3 datos se inventa (CLAUDE.MD sec. 0.6): en vez de fabricar un shape
 * para algo que no existe, {@link ResumenHome#bloqueos()} documenta explicitamente, en la
 * propia respuesta, por que cada uno falta — hasta que el modulo dueno agregue el finder
 * correspondiente en su {@code api} publica.
 */
public interface ConsultarResumenHomeUseCase {

    ResumenHome consultar(UserId actorId);

    record ResumenHome(int puntosLiga, BigDecimal coherencia, int rachaActual, int rachaMaxima,
                        List<String> bloqueos) {
    }
}
