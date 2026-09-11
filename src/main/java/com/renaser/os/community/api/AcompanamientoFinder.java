package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lo que `community` expone sobre el acompañamiento: quién acompaña a quién y entre qué fechas.
 *
 * <p><b>Por qué existe.</b> El historial de asignaciones es el agregado
 * {@code AsignacionCelula}, interno de este módulo. El seguimiento semanal y la evaluación
 * mensual lo necesitan, pero viven afuera (en {@code mentoring}) porque cruzan hacia
 * {@code habits} y {@code evidence}, y meterlos acá cerraría el ciclo
 * {@code community → evidence → points → community} que Spring Modulith rechaza — {@code points}
 * ya depende de {@code community} para el ranking de células.
 *
 * <p>Devuelve records planos, nunca el agregado: quien consume no puede cerrar un intervalo por
 * accidente.
 */
public interface AcompanamientoFinder {

    /**
     * Tramos en los que {@code mentorId} acompañó, recortados a la ventana pedida.
     *
     * @param hasta {@code null} = abierto hacia adelante.
     */
    List<TramoDeAcompanamiento> tramosDeMentor(UserId mentorId, Instant desde, Instant hasta);

    /** Aprendices con pertenencia vigente a ese grupo en ese instante. */
    List<UserId> aprendicesVigentes(UUID grupoId, Instant instante);

    /**
     * Tramos de pertenencia de cada aprendiz a ese grupo, recortados a la ventana. Es lo que
     * permite atribuir una obligación al mentor que efectivamente estaba: la ventana real es la
     * intersección de las dos pertenencias.
     */
    List<TramoDeAprendiz> tramosDeAprendices(UUID grupoId, Instant desde, Instant hasta);

    /**
     * TODOS los que pertenecen hoy al grupo: aprendices, mentor, guías y soporte.
     *
     * <p>Es la "lista deseada" contra la que el chat reconcilia sus participantes. Se pide
     * entera y no como diferencia: así un evento reentregado fuera de orden no puede
     * reincorporar a quien ya salió.
     */
    List<UserId> integrantesVigentes(UUID grupoId, Instant instante);

    /**
     * Si {@code usuarioId} pertenece hoy al grupo, con cualquier función.
     *
     * <p>Existe para que el chat pueda revalidar en cada acción en vez de confiar en su
     * proyección de participantes: la proyección acelera las consultas, pero si se queda vieja
     * concede acceso a alguien que ya rotó (plan.md §6).
     */
    boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante);

    /**
     * Si {@code actorId} acompaña VIGENTEMENTE ese grupo. Vigente, no "alguna vez": un exmentor
     * con el token todavía válido no pasa.
     */
    boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante);

    /** Nombre y cohorte de un grupo, sin exponer el agregado. */
    java.util.Optional<GrupoBasico> grupo(UUID grupoId);

    /**
     * Grupos regulares con mentor vigente, para el barrido de avisos. La recepción queda fuera:
     * es transitoria y la atienden guías, no un mentor evaluado.
     */
    List<GrupoAcompanado> gruposConMentorVigente(Instant instante);

    /** {@code hasta} null = el tramo sigue abierto. Semiabierto {@code [desde, hasta)}. */
    record TramoDeAcompanamiento(UUID grupoId, String grupoNombre, Instant desde, Instant hasta) {
    }

    record TramoDeAprendiz(UserId aprendizId, Instant desde, Instant hasta) {
    }

    record GrupoBasico(UUID grupoId, String nombre, UUID cohorteId, String zonaHoraria) {
    }

    /**
     * @param diasSinActividadAlerta umbral de la política de su cohorte (P-06). Viaja acá para
     *                               que quien barre no tenga que volver a preguntar por cohorte.
     */
    record GrupoAcompanado(UUID grupoId, String nombre, UserId mentorId, UUID cohorteId, String zonaHoraria,
                            int diasSinActividadAlerta) {
    }
}
