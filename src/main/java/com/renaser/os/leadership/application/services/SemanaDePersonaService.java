package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.application.services.LecturaDeLaFicha.Lectura;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
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

/**
 * La ficha de una persona para el líder (spec 005, US1): su semana del semáforo día por día, con lo que la respalda.
 *
 * <p>Autoriza primero y lee después: el camino que termina en 403 o en 404 no pide nada ni deja registro. Cada llamada
 * que llega a leer REGISTRA la consulta (FR-016) en la misma transacción: si no se puede dejar el registro, no se
 * muestra la ficha. Una consulta equivocada no se registra: una semana futura o una persona de otro grupo se rechazan
 * antes.
 *
 * <p>La semana es la del semáforo (R-2), de sábado a viernes, no la de lunes a domingo del mentor: la ventana la fija
 * {@link VentanaDeLaFicha} con el «hoy» de la persona (sin fecha, la semana en curso), y {@code mentoring} arma la
 * semana con ella.
 */
@Service
public class SemanaDePersonaService implements ConsultarSemanaDePersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final LecturaDeLaFicha lecturas;
    private final GuardarConsultaDeFichaPort consultas;
    private final IdGenerator idGenerator;
    private final Clock clock;

    SemanaDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, LecturaDeLaFicha lecturas,
                           GuardarConsultaDeFichaPort consultas, IdGenerator idGenerator, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
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
        // El «hoy» de la persona, no el del servidor (regla 02): sin fecha, la semana en curso; rechaza un dia que no
        // es viernes o una semana futura.
        VentanaDeLaFicha ventana = VentanaDeLaFicha.paraLaFicha(consulta.semanaHasta(),
                ahora.atZone(miembro.participacion().zona()).toLocalDate());
        Lectura lectura = lecturas.leer(miembro, grupo, ventana);
        registrar(consulta.actorId(), rol, miembro, new Registro(grupo, ventana));
        return ArmadoDeLaFicha.armar(miembro, ventana, lectura);
    }

    private void registrar(UserId actorId, UserRole rol, Miembro miembro, Registro donde) {
        Contenido contenido = new Contenido(actorId, rol, miembro.perfil().id(), donde.grupo().grupoId(),
                donde.grupo().mentorId(), donde.ventana().desde());
        consultas.insertar(ConsultaDeFicha.registrar(idGenerator.newId(), contenido, clock));
    }

    /** En qué grupo y qué ventana se miró: lo que una consulta anota además de quién miró a quién. */
    private record Registro(GrupoAcompanado grupo, VentanaDeLaFicha ventana) {
    }
}
