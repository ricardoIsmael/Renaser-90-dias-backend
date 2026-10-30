package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarHuecosDePersonaUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha.Contenido;
import com.renaser.os.mentoring.api.HuecosDeHorarioFinder;
import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Los huecos de horario de una persona para el líder (spec 015). Autoriza primero y lee después, igual que
 * {@link SemanaDePersonaService}: el camino que termina en 403 o en 404 no pide nada ni deja registro. Quien puede verlo
 * es el mismo que ve su semana: MENTOR_LEAD, ADMIN o ALCHEMIST con la cuenta activa, de un grupo regular con mentor y
 * una persona que hoy es de ese grupo ({@code AccesoDeLiderazgo}, {@code PersonasDelGrupo}).
 *
 * <p><b>Deja constancia de la consulta</b> con el mismo puerto y la misma tabla que la ficha de la semana
 * ({@code consultas_de_ficha}, spec 005 FR-016; decisión del dueño del 2026-10-29, spec 015 FR-027): quién, con qué
 * rol, a quién, de qué grupo, a qué mentor y el lunes de la semana que miró. Se registra DESPUÉS de leer y en la misma
 * transacción: si la lectura falla (semana inválida, persona no encontrada, cuenta suspendida) no queda nada, y si no se
 * puede dejar el registro no se entrega la lectura.
 *
 * <p><b>Cómo entra al resumen del mentor</b> ({@code ResumenDeConsultasService}, las 20:00): igual que cualquier otra
 * consulta de ficha. La tabla no distingue qué se miró, y el resumen junta a las personas SIN repetir a nadie
 * ({@code ReglaDelResumenDeConsultas.resumir}) y publica un solo aviso por mentor y día: abrir los huecos y la semana de
 * la misma persona el mismo día cuenta una vez. Como allá, solo cuenta al líder de mentores; lo que miran Administración
 * y Alquimista queda registrado pero no avisa.
 */
@Service
public class HuecosDePersonaService implements ConsultarHuecosDePersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final HuecosDeHorarioFinder huecos;
    private final GuardarConsultaDeFichaPort consultas;
    private final IdGenerator idGenerator;
    private final Clock clock;

    HuecosDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, HuecosDeHorarioFinder huecos,
                           GuardarConsultaDeFichaPort consultas, IdGenerator idGenerator, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.huecos = huecos;
        this.consultas = consultas;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public HuecosDeLaSemana huecosDe(ConsultaHuecosDePersona consulta) {
        UserRole rol = acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        HuecosDeLaSemana semana = huecos.semanaDe(miembro.perfil().id(), consulta.semana());
        // El lunes que de verdad se miró (la respuesta lo trae resuelto aunque no se haya pedido uno), no el pedido.
        registrar(consulta.actorId(), rol, miembro, grupo, semana);
        return semana;
    }

    private void registrar(UserId actorId, UserRole rol, Miembro miembro, GrupoAcompanado grupo,
                           HuecosDeLaSemana semana) {
        Contenido contenido = new Contenido(actorId, rol, miembro.perfil().id(), grupo.grupoId(), grupo.mentorId(),
                semana.semana());
        consultas.insertar(ConsultaDeFicha.registrar(idGenerator.newId(), contenido, clock));
    }
}
