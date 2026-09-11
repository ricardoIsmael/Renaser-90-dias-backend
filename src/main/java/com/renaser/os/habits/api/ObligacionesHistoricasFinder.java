package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * "Qué le tocaba a esta gente, entre estas dos fechas."
 *
 * <p><b>Por qué existe.</b> {@link AgendaDelDiaFinder} responde por HOY y por UNA persona: sirve
 * para la pantalla del propio aprendiz y no para el seguimiento de un mentor, que necesita una
 * semana entera de diez personas. Resolverlo con la agenda sería 70 consultas para pintar una
 * pantalla, y encima no podría mirar hacia atrás.
 *
 * <p><b>En lote, siempre.</b> Una consulta por rango y conjunto de participantes, apoyada en
 * {@code registros_dia_idx} ({@code participante_id, fecha_ejecucion}). Nunca de a uno.
 *
 * <p><b>No recibe {@code actorId}, a propósito</b> — mismo criterio que
 * {@link AgendaDelDiaFinder} y {@code evidence.RegistrosConEvidenciaFinder}: quien llama ya
 * autorizó. En el consumidor de hoy, {@code community} comprobó que el actor acompaña
 * vigentemente a ese grupo antes de armar la lista de participantes. Este puerto responde por
 * los ids que le den; no verifica de quién son.
 */
public interface ObligacionesHistoricasFinder {

    /**
     * @param desde inclusivo, @param hasta inclusivo, en fechas locales del participante ya
     *              resueltas por quien llama.
     * @return orden estable por participante y fecha; lista vacía si no hay nada, nunca null.
     */
    List<ObligacionHabito> porParticipantesEntre(Collection<UserId> participantes, LocalDate desde,
                                                  LocalDate hasta);
}
