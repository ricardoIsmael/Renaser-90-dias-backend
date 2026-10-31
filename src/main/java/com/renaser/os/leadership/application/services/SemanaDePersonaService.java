package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.application.services.LecturaDeLaFicha.Lectura;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha.Contenido;
import com.renaser.os.leadership.domain.model.cumplimiento.VentanaDeLaFicha;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * La ficha de una persona para el líder (spec 005, US1): su semana del semáforo día por día, con lo que la respalda.
 *
 * <p>Autoriza primero y lee después: el camino que termina en 403 o en 404 no pide nada ni deja registro. Cada llamada
 * que llega a leer REGISTRA la consulta (FR-016) en la misma transacción: si no se puede dejar el registro, no se
 * muestra la ficha. Una consulta equivocada no se registra: una semana futura o una persona de otro grupo se rechazan
 * antes.
 *
 * <p>La ventana es la del resumen por grupos (R-2, decisión del dueño del 2026-10-19), no la de lunes a domingo del
 * mentor: la fija {@link VentanaDeLaFicha} con el «hoy» de la persona (sin fecha, los siete días que terminan ayer; con
 * un viernes, esa semana de sábado a viernes), y {@code mentoring} arma la semana con ella.
 *
 * <p>Se puede llegar a la persona por su grupo o, desde la spec 021, sin pasar por uno: las dos puertas solo cambian
 * cómo se resuelve a quién se mira ({@link Mirada}); la ventana, la lectura, el armado y el registro son los mismos.
 */
@Service
public class SemanaDePersonaService implements ConsultarSemanaDePersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final PersonasDelPrograma programa;
    private final LecturaDeLaFicha lecturas;
    private final GuardarConsultaDeFichaPort consultas;
    private final IdGenerator idGenerator;
    private final Clock clock;

    SemanaDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, PersonasDelPrograma programa,
                           LecturaDeLaFicha lecturas, GuardarConsultaDeFichaPort consultas, IdGenerator idGenerator,
                           Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.programa = programa;
        this.lecturas = lecturas;
        this.consultas = consultas;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SemanaDeLaPersona semanaDe(ConsultaSemanaDePersona consulta) {
        UserRole rol = acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        return leerYRegistrar(consulta.actorId(), rol, Mirada.deUnGrupo(miembro, grupo), consulta.semanaHasta(), ahora);
    }

    @Override
    @Transactional
    public SemanaDeLaPersona semanaDeUnaPersona(ConsultaSemanaDeUnaPersona consulta) {
        UserRole rol = acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        Mirada mirada = programa.mirar(UserId.of(consulta.personaId()), ahora);
        return leerYRegistrar(consulta.actorId(), rol, mirada, consulta.semanaHasta(), ahora);
    }

    private SemanaDeLaPersona leerYRegistrar(UserId actorId, UserRole rol, Mirada mirada, LocalDate semanaHasta,
                                             Instant ahora) {
        // El «hoy» de la persona, no el del servidor (regla 02): sin fecha, la ventana vigente del resumen; rechaza un
        // dia que no es viernes o una semana futura.
        VentanaDeLaFicha ventana = VentanaDeLaFicha.de(semanaHasta,
                ahora.atZone(mirada.miembro().participacion().zona()).toLocalDate());
        Lectura lectura = lecturas.leer(mirada, ventana);
        registrar(actorId, rol, mirada, ventana);
        return ArmadoDeLaFicha.armar(mirada.miembro(), ventana, lectura);
    }

    private void registrar(UserId actorId, UserRole rol, Mirada mirada, VentanaDeLaFicha ventana) {
        Contenido contenido = new Contenido(actorId, rol, mirada.personaId(), mirada.grupoId(), mirada.mentorId(),
                ventana.desde());
        consultas.insertar(ConsultaDeFicha.registrar(idGenerator.newId(), contenido, clock));
    }
}
