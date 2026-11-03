package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.application.ports.in.ConsultarAnalisisDePersonaUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha.Contenido;
import com.renaser.os.rag.api.Analisis8020Finder;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.ClavesDeFuncionalidad;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * El 80/20 y el análisis de una persona para el líder (spec 031). Autoriza primero y lee después, igual que {@link CierresDePersonaService}:
 * el camino que termina en 403 o en 404 no pide nada ni deja registro. Quien lo ve es el mismo que ve su semana y su Radar:
 * MENTOR_LEAD, ADMIN o ALCHEMIST con la cuenta activa, de un grupo regular con mentor y una persona que hoy es de ese grupo (o, sin pasar
 * por un grupo, cualquier aprendiz que se muestre y sea de una formación que el líder ve: {@code PersonasDelPrograma}).
 *
 * <p><b>Respeta «Código Renaser en las fichas»</b> (spec 034, 031-2 A): el análisis resume lo que la persona respondió en el Código Renaser,
 * así que el líder no lo ve con el interruptor apagado (404); Administración y Alquimista, que el interruptor no gobierna, sí. Va después
 * de autorizar y antes de leer.
 *
 * <p><b>Deja constancia de la consulta</b> con el mismo puerto y la misma tabla que el resto de la ficha ({@code consultas_de_ficha},
 * FR-021): se registra DESPUÉS de leer y en la misma transacción, y registra el primer día del periodo que de verdad se miró.
 */
@Service
public class AnalisisDePersonaService implements ConsultarAnalisisDePersonaUseCase {

    private final AccesoDeLiderazgo acceso;
    private final PersonasDelGrupo personas;
    private final PersonasDelPrograma programa;
    private final Analisis8020Finder analisis;
    private final Interruptores interruptores;
    private final GuardarConsultaDeFichaPort consultas;
    private final IdGenerator idGenerator;
    private final Clock clock;

    AnalisisDePersonaService(AccesoDeLiderazgo acceso, PersonasDelGrupo personas, PersonasDelPrograma programa,
                             Analisis8020Finder analisis, Interruptores interruptores, GuardarConsultaDeFichaPort consultas,
                             IdGenerator idGenerator, Clock clock) {
        this.acceso = acceso;
        this.personas = personas;
        this.programa = programa;
        this.analisis = analisis;
        this.interruptores = interruptores;
        this.consultas = consultas;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public VistaDelAnalisis analisisDe(ConsultaAnalisisDePersona consulta) {
        UserRole rol = acceso.requireLiderazgoActivo(consulta.actorId());
        exigirQueSePuedaVer(consulta.actorId());
        Instant ahora = clock.now();
        GrupoAcompanado grupo = personas.requireGrupoConMentor(consulta.actorId(), consulta.grupoId(), ahora);
        Miembro miembro = personas.requireMiembro(grupo, UserId.of(consulta.personaId()), ahora);
        return leerYRegistrar(consulta.actorId(), rol, Mirada.deUnGrupo(miembro, grupo), consulta.tipo(), consulta.inicio());
    }

    @Override
    @Transactional
    public VistaDelAnalisis analisisDeUnaPersona(ConsultaAnalisisDeUnaPersona consulta) {
        UserRole rol = acceso.requireLiderazgoActivo(consulta.actorId());
        exigirQueSePuedaVer(consulta.actorId());
        Mirada mirada = programa.mirar(consulta.actorId(), UserId.of(consulta.personaId()), clock.now());
        return leerYRegistrar(consulta.actorId(), rol, mirada, consulta.tipo(), consulta.inicio());
    }

    /** Spec 034 (D-299): Administración decide si el líder ve lo del Código Renaser. Apagado, «no disponible» (404). */
    private void exigirQueSePuedaVer(UserId actor) {
        interruptores.exigirPrendidaPara(ClavesDeFuncionalidad.RADAR_EN_FICHAS, actor);
    }

    private VistaDelAnalisis leerYRegistrar(UserId actorId, UserRole rol, Mirada mirada, Tipo tipo, LocalDate inicio) {
        VistaDelAnalisis vista = analisis.de(mirada.personaId(), tipo, inicio);
        Contenido contenido = new Contenido(actorId, rol, mirada.personaId(), mirada.grupoId(), mirada.mentorId(), vista.inicio());
        consultas.insertar(ConsultaDeFicha.registrar(idGenerator.newId(), contenido, clock));
        return vista;
    }
}
