package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * D-43 (docs/MODULOS_A_AVANZAR.md §8) — expone el % de cumplimiento de
 * habitos EN LOTE para que {@code points} lo combine con el de
 * {@code rocks}/{@code academy} (50% habitos + 35% rocas + 15% cursos) sin el
 * N+1 que tumbaba el ranking general en el backend viejo ("Too many database
 * connections opened" con ~30 cuentas activas, verificado en vivo el
 * 2026-08-12 — ver la cabecera de
 * {@code prisma/migrations/general_ranking_scores_function.sql} en el repo
 * viejo). Formula portada literal de
 * {@code src/lib/coherence.ts::averageCompletionForDates} — citas
 * archivo:linea completas en docs/MODULO_HABITS.md §9.
 *
 * <h2>Por que {@link BigDecimal} y no un entero redondeado</h2>
 * El encargo original sugeria {@code Map<UserId, Integer>}. Se cambio
 * deliberadamente: tanto {@code coherence.ts} como la funcion SQL vieja
 * {@code general_ranking_scores()} ya redondean DOS veces antes de llegar a
 * este numero — por dia, y luego el promedio a 1 decimal
 * ({@code round(avg(day_score) * 10) / 10}). Redondear una TERCERA vez a
 * entero aca introduciria un error de precision que el sistema viejo nunca
 * tuvo, justo cuando D-43 pide portar la formula LITERAL sin recalibrar
 * ningun criterio. {@code points} redondea a 1 decimal otra vez recien al
 * combinar los tres pesos — igual que hacia {@code generalRankingService.ts}.

 *
 * <p><b>Por que vive en `points` y no en el modulo que lo implementa (DIP):</b> declararlo
 * en el modulo proveedor creaba un CICLO que Spring Modulith rechaza — `habits` ya depende
 * de `points` para otorgar puntos al completar, asi que `points` no puede depender de
 * `habits` en la otra direccion. Invirtiendo la dependencia, el consumidor declara lo que
 * necesita y el proveedor lo implementa: la flecha queda en un solo sentido.
 */
public interface PorcentajeHabitosFinder {

    /**
     * @param participantes participantes a calcular (se deduplican; una coleccion vacia
     *                      devuelve un mapa vacio sin consultar la base)
     * @param hasta         ultimo dia (UTC, inclusive) de la ventana de 7 dias cerrados —
     *                      la ventana real consultada es [hasta - 6, hasta]
     * @return              el mapa devuelto tiene UNA entrada por CADA participante pedido — nunca
     *                      se omite uno (a diferencia de {@code COALESCE(hp.pct, 100)} en la funcion
     *                      SQL vieja, donde el LEFT JOIN simplemente no trae fila y el caller debe
     *                      saber aplicar el default). Un participante sin ningun registro calificable
     *                      en la ventana aparece en el mapa con el valor explicito 100.0 ("recien
     *                      empezo", no se lo castiga) — {@code points} puede confiar en que
     *                      {@code resultado.get(participanteId)} nunca es {@code null} para ningun
     *                      id de la coleccion pedida.
     */
    Map<UserId, BigDecimal> porcentajePorParticipante(Collection<UserId> participantes, LocalDate hasta);
}
