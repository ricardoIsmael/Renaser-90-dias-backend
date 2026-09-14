package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.acompanamiento.IngresarAlGrupoDeRecepcionUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.ConsultarRecepcionVigentePort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Mete a quien acaba de registrarse en el grupo de bienvenida vigente.
 *
 * <p>Es lo UNICO automatico que queda del acompanamiento. El grupo lo crea y lo programa el
 * administrador —nombre, siete dias, mentor—; automatica es solo la entrada, porque pedirle que
 * meta a mano a cada persona el dia que se registra convertiria el alta en una tarea suya.
 *
 * <p><b>Entrar a un grupo son TRES escrituras, no una (2026-09-14).</b> Hasta esta revision este
 * servicio hacia solo la primera —abrir el intervalo en {@code asignaciones_celula}— y el
 * resultado era que la bienvenida automatica escribia una fila que no leia nadie:
 *
 * <ul>
 *   <li>{@code GET /me/cell} y {@code /me/cell/members} resuelven el grupo por
 *       {@code participantes_programa.celula_id} (ver {@code ConsultarCelulaDeParticipantePort}),
 *       o sea el PUNTERO — no el historial. Sin sincronizarlo, la persona entraba al grupo y su
 *       app seguia diciendo "Todavia no tienes un mentor asignado".</li>
 *   <li>La lista de "aprendices disponibles" del panel lee el mismo puntero, asi que el
 *       administrador los seguia viendo como gente sin grupo para siempre.</li>
 *   <li>El chat del grupo reconcilia su lista con {@link ComposicionDeCelulaCambiadaEvent}. Sin
 *       publicarlo, quien entraba por la bienvenida no aparecia en la conversacion.</li>
 * </ul>
 *
 * <p>Los otros tres servicios que asignan gente —{@link ComposicionDeCelulaService},
 * {@link TrasladoService} y {@link RotacionService}— ya hacian las tres. Este era el unico que
 * no, y justo el unico automatico. Es el mismo defecto que el javadoc de
 * {@code ComposicionDeCelulaService} describe al reves: "el grupo se veia armado en
 * administracion y vacio en todo lo demas".
 *
 * <p>No se noto porque su prueba unitaria solo miraba el {@code SaveAsignacionPort} falso, que es
 * precisamente la escritura que si estaba. Mismo punto ciego que E-180.
 */
@Service
public class IngresoARecepcionService implements IngresarAlGrupoDeRecepcionUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngresoARecepcionService.class);

    private final ConsultarRecepcionVigentePort recepcionVigentePort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final SaveAsignacionPort saveAsignacionPort;
    private final ParticipacionProgramaFinder participacionFinder;
    private final AsignacionCelulaPort asignacionCelulaPort;
    private final ApplicationEventPublisher eventos;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public IngresoARecepcionService(ConsultarRecepcionVigentePort recepcionVigentePort,
                                     LoadAsignacionesPort loadAsignacionesPort,
                                     SaveAsignacionPort saveAsignacionPort,
                                     ParticipacionProgramaFinder participacionFinder,
                                     AsignacionCelulaPort asignacionCelulaPort,
                                     ApplicationEventPublisher eventos,
                                     IdGenerator idGenerator, Clock clock) {
        this.recepcionVigentePort = recepcionVigentePort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.saveAsignacionPort = saveAsignacionPort;
        this.participacionFinder = participacionFinder;
        this.asignacionCelulaPort = asignacionCelulaPort;
        this.eventos = eventos;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void ingresar(UserId usuarioId) {
        /* Solo aprendices. Un mentor o un administrador que se da de alta no entra a recibir la
           bienvenida: acompana, no la cursa. El evento de registro no dice el rol, asi que se
           consulta -- deducirlo del contexto es justo lo que fallaba en E-169. */
        UserRole rol = participacionFinder.deParticipante(usuarioId)
                .map(com.renaser.os.users.api.ParticipacionPrograma::rol)
                .orElse(UserRole.TRAINEE);
        if (rol != UserRole.TRAINEE) {
            return;
        }

        /* Ya tiene un grupo vivo: no se toca. Puede pasar si el administrador lo coloco a mano
           antes de que llegara el evento, y en ese caso su decision manda sobre la automatica.
           Ademas, insertar aqui chocaria contra `asignaciones_un_grupo_por_aprendiz`. */
        boolean yaTieneGrupo = loadAsignacionesPort.porUsuario(usuarioId).stream()
                .anyMatch(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ && a.vigenteEn(clock.now()));
        if (yaTieneGrupo) {
            return;
        }

        LocalDate hoy = clock.now().atZone(ZoneId.of(PoliticaMentoria.ZONA_POR_DEFECTO)).toLocalDate();
        Optional<CelulaId> recepcion = recepcionVigentePort.recepcionVigenteEn(hoy);
        if (recepcion.isEmpty()) {
            /* Que no haya recepcion abierta NO es un fallo del sistema: es una tarea pendiente del
               administrador. Pero tampoco puede pasar en silencio -- significa que quien acaba de
               registrarse se queda sin grupo de bienvenida y nadie se entera. */
            log.warn("[community.IngresoARecepcionService] {} se registro y no hay grupo de recepcion "
                    + "vigente el {}: entra sin bienvenida", usuarioId, hoy);
            return;
        }

        Instant ahora = clock.now();
        /* La clave se deriva del USUARIO, no del instante: el outbox de Modulith es at-least-once
           y puede reentregar el mismo registro. Con una clave por instante, la reentrega abriria
           un segundo intervalo; con esta, choca contra `asignaciones_celula_operacion_uk` y no
           pasa nada. */
        String claveOperacion = "recepcion-alta|" + usuarioId.value();
        if (loadAsignacionesPort.porClaveOperacion(claveOperacion).isPresent()) {
            return;
        }

        CelulaId grupo = recepcion.get();
        saveAsignacionPort.save(AsignacionCelula.abrir(AsignacionId.of(idGenerator.newId()),
                grupo, usuarioId, FuncionAcompanamiento.APRENDIZ, ahora,
                MotivoAsignacion.RECEPCION, null, claveOperacion));
        sincronizarPunteros(usuarioId, grupo, ahora);
        eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(grupo.value(), ahora));
        log.info("[community.IngresoARecepcionService] {} entro al grupo de recepcion {}",
                usuarioId, grupo.value());
    }

    /**
     * Los punteros de proyeccion de {@code participantes_programa}: el grupo y su mentor vigente.
     *
     * <p>El mentor sale del INTERVALO abierto en {@code asignaciones_celula} y no de
     * {@code celulas.mentor_id}, aunque las dos cosas se mantengan sincronizadas. El intervalo es
     * la fuente de verdad y la columna su proyeccion; leer una proyeccion para escribir otra es
     * como se encadenan los desfases, que es exactamente el defecto que esta correccion repara.
     *
     * <p>Un grupo de bienvenida puede no tener mentor todavia (D-05). {@code null} entonces limpia
     * el puntero en vez de dejar el anterior, que seria mentira — lo dice el contrato del puerto.
     *
     * <p>Va por {@code sincronizarAcompanamiento} y no por {@code asignarCelula} porque aqui no
     * hay actor humano: esto lo dispara el alta, no un administrador.
     */
    private void sincronizarPunteros(UserId aprendizId, CelulaId grupo, Instant ahora) {
        UserId mentorVigente = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(grupo))
                .mentorVigenteEn(grupo, ahora).orElse(null);
        asignacionCelulaPort.sincronizarAcompanamiento(aprendizId, grupo.value(), mentorVigente);
    }
}
