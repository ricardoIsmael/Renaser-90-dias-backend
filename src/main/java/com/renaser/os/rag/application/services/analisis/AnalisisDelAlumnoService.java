package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.rag.api.Analisis8020Finder;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.application.ports.in.analisis.ConsultarAnalisisDeAlumnoUseCase;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.ClavesDeFuncionalidad;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * El análisis de un alumno para su mentor o su guía (spec 031). Mismo guardián que el Código Renaser, los huecos y los cierres del alumno
 * (copiado a propósito, ARF-15): el acompañante VIGENTE del grupo, un alumno que hoy es de ESE grupo y la cuenta del actor activa (el
 * interceptor no mira a MENTOR, A-1, E-258).
 *
 * <p>Después, dos candados más. <b>La formación</b> (spec 022): lo que es de otra formación responde 404 como si no existiera. <b>El
 * interruptor «Código Renaser en las fichas»</b> (spec 034, 031-2 A): el análisis lee lo que la persona respondió en el Código Renaser,
 * así que quien no lo puede ver crudo tampoco ve su resumen; apagado, 404. Autoriza primero y lee después: ningún camino que termine en
 * 403 o 404 pide nada ni averigua si existe un análisis.
 */
@Service
public class AnalisisDelAlumnoService implements ConsultarAnalisisDeAlumnoUseCase {

    private final AcompanamientoFinder acompanamiento;
    private final UserSummaryFinder usuarios;
    private final AlcanceDeFormacionFinder formaciones;
    private final Interruptores interruptores;
    private final Analisis8020Finder analisis;
    private final Clock clock;

    AnalisisDelAlumnoService(AcompanamientoFinder acompanamiento, UserSummaryFinder usuarios, AlcanceDeFormacionFinder formaciones,
                             Interruptores interruptores, Analisis8020Finder analisis, Clock clock) {
        this.acompanamiento = acompanamiento;
        this.usuarios = usuarios;
        this.formaciones = formaciones;
        this.interruptores = interruptores;
        this.analisis = analisis;
        this.clock = clock;
    }

    @Override
    public VistaDelAnalisis deAlumno(ConsultaDeAnalisisDeAlumno consulta) {
        Instant ahora = clock.now();
        if (!acompanamiento.acompanaVigente(consulta.actorId(), consulta.grupoId(), ahora)) {
            throw new NotAuthorizedException("No acompañas ese grupo");
        }
        requireCuentaActiva(consulta.actorId());
        if (!acompanamiento.aprendicesVigentes(consulta.grupoId(), ahora).contains(consulta.alumnoId())) {
            throw new NotAuthorizedException("Ese alumno no pertenece al grupo que acompañas");
        }
        formaciones.exigirQueLaVea(consulta.actorId(), consulta.alumnoId());
        interruptores.exigirPrendidaPara(ClavesDeFuncionalidad.RADAR_EN_FICHAS, consulta.actorId());
        return analisis.de(consulta.alumnoId(), Analisis8020Finder.Tipo.valueOf(consulta.tipo().name()), consulta.inicio());
    }

    private void requireCuentaActiva(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta está suspendida");
        }
    }
}
