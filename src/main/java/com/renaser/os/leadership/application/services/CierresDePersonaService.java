package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarCierresDePersonaUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha.Contenido;
import com.renaser.os.rocks.api.CierresDelDiaFinder;
import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Los cierres del día de una persona para el líder (spec 017). Autoriza primero y lee después, igual que
 * {@link SemanaDePersonaService}: el camino que termina en 403 o en 404 no pide nada ni deja registro. Quien puede verlo es el mismo
 * que ve su semana: MENTOR_LEAD, ADMIN o ALCHEMIST con la cuenta activa, de un grupo regular con mentor y una persona que hoy es de
 * ese grupo ({@code AccesoDeLiderazgo}, {@code PersonasDelGrupo}).
 *
 * <p><b>Deja constancia de la consulta</b> con el mismo puerto y la misma tabla que la ficha de la semana y los huecos
 * ({@code consultas_de_ficha}, spec 005 FR-016; spec 015 FR-027): quién, con qué rol, a quién, de qué grupo, a qué mentor y el lunes
 * que miró. Es lo que el dueño decidió para los huecos y lo que la persona escribe en su cierre es más personal todavía. Se registra
 * DESPUÉS de leer y en la misma transacción: si la lectura falla (semana inválida, persona suspendida) no queda nada, y si no se
 * puede dejar el registro no se entrega la lectura. El resumen de las 20:00 al mentor cuenta a cada persona una sola vez al día.
 */
@Service
public class CierresDePersonaService implements ConsultarCierresDePersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final CierresDelDiaFinder cierres;
    private final GuardarConsultaDeFichaPort consultas;
    private final IdGenerator idGenerator;
    private final Clock clock;

    CierresDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, CierresDelDiaFinder cierres,
                            GuardarConsultaDeFichaPort consultas, IdGenerator idGenerator, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.cierres = cierres;
        this.consultas = consultas;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CierresDeLaSemana cierresDe(ConsultaCierresDePersona consulta) {
        UserRole rol = acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        CierresDeLaSemana semana = cierres.semanaDe(miembro.perfil().id(), consulta.semana());
        // El lunes que de verdad se miró (la respuesta lo trae resuelto aunque no se haya pedido uno), no el pedido.
        registrar(consulta.actorId(), rol, miembro, grupo, semana);
        return semana;
    }

    private void registrar(UserId actorId, UserRole rol, Miembro miembro, GrupoAcompanado grupo, CierresDeLaSemana semana) {
        Contenido contenido = new Contenido(actorId, rol, miembro.perfil().id(), grupo.grupoId(), grupo.mentorId(),
                semana.semana());
        consultas.insertar(ConsultaDeFicha.registrar(idGenerator.newId(), contenido, clock));
    }
}
