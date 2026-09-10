package com.renaser.os.community.application.ports.in.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.CoberturaCelula;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una sola lectura que responde "¿qué acompaño y qué curso yo?".
 *
 * <p>Existe para que el cliente deje de encadenar {@code /admin/cohorts} →
 * {@code /admin/cells?cohortId} → {@code /admin/cells/{id}} y quedarse con el primer
 * elemento de cada lista. Eso obligaba a un mentor a pasar por superficie de administración
 * y, peor, elegía la primera célula devuelta en vez de la suya (contracts.md: "no depender
 * del primer elemento devuelto").
 */
public interface ConsultarContextoAcompanamientoUseCase {

    ContextoAcompanamiento contexto(UserId actorId);

    /**
     * @param participaEnPrograma si tiene su propio programa de 90 días. Para un mentor es
     *                            opcional (D-07) y su ausencia no bloquea el acompañamiento.
     * @param diaDePrograma       {@code null} cuando no participa. Nunca 0 para decir "no sé".
     * @param puedeAcompanar      si tiene al menos una asignación vigente de acompañamiento.
     */
    record ContextoAcompanamiento(boolean participaEnPrograma, Integer diaDePrograma, boolean puedeAcompanar,
                                   Capacidades capacidades, List<AsignacionResumen> asignaciones) {
    }

    /**
     * Qué puede hacer esta persona con SU propio programa. Lo decide el servidor porque depende
     * del rol, y el cliente no debe deducirlo: si lo dedujera mal, le mostraría a un mentor un
     * onboarding obligatorio que no le corresponde — que es exactamente el bloqueo que RF-01 y
     * RF-02 vienen a levantar.
     *
     * @param programaObligatorio el aprendiz DEBE completar su onboarding; el mentor no (D-07).
     *                            Es lo que decide si la app puede dejar pasar sin programa.
     * @param puedeActivarPrograma tiene rol para iniciarlo y todavía no lo hizo. Falso tanto para
     *                             quien ya lo activó como para quien no puede.
     * @param administrar         si puede abrir Administracion (SDD 003, ARF-01/15). Sale de la
     *                            MISMA condicion que ya exigen los guards administrativos —rol
     *                            ADMIN/ALQUIMISTA y cuenta activa—, no de {@code UserRole.can},
     *                            que para esos dos roles todavia responde {@code true} a todo y
     *                            serializarlo seria publicar una matriz que nadie audito.
     *                            <b>Decide que se MUESTRA, nunca que se puede hacer:</b> cada
     *                            endpoint vuelve a autorizar, asi que un cliente que lo falsee ve
     *                            pantallas vacias, no datos de nadie.
     */
    record Capacidades(boolean programaObligatorio, boolean puedeActivarPrograma, boolean acompanar,
                        boolean administrar) {
    }

    /**
     * @param cobertura quién responde por el grupo ahora. Un grupo sin mentor sigue siendo un
     *                  grupo: el cliente no debe mostrar "no tienes grupo" (plan.md §10).
     * @param hasta     {@code null} mientras la asignación siga vigente.
     */
    record AsignacionResumen(UUID grupoId, String grupoNombre, UUID cohorteId, TipoCelula tipo,
                              FuncionAcompanamiento funcion, Instant desde, Instant hasta,
                              CoberturaCelula cobertura, int aprendices, Integer cupo) {
    }
}
