package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.ports.in.registro.CerrarPostDiarioComunidadUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Implementa {@link CerrarPostDiarioComunidadUseCase}. Misma forma que
 * {@code ClaseDiariaHabitoService} y {@code PastillaRenacerHabitoService}: localiza el registro
 * del habito y delega el cierre en {@link CompletarRegistroUseCase}, que es donde viven —en un
 * solo lugar— el calculo de puntos, la ventana de entrega, el bloqueo pesimista y el evento de
 * dominio.
 *
 * <p><b>Dos guardas de idempotencia, en este orden</b>, porque el disparador es un evento del
 * outbox y puede reentregarse:
 *
 * <ol>
 *   <li>El registro se busca por el DIA DE LA PUBLICACION en la zona del participante, no por
 *       "hoy" — una reentrega al dia siguiente vuelve a apuntar al mismo registro, no al de
 *       manana (ver javadoc del puerto).</li>
 *   <li>Si ese registro ya esta en estado terminal, se vuelve sin tocarlo. Cubre los dos casos,
 *       el comun y el de carrera, porque la busqueda se hace CON el bloqueo pesimista: dos
 *       publicaciones el mismo dia o una reentrega tras un reinicio leen el estado ya
 *       terminal, y dos caminos simultaneos (este oyente y el {@code POST /complete} que el
 *       cliente movil todavia hace al publicar) quedan serializados por el cerrojo, asi que el
 *       segundo lee {@code COMPLETADO}. Nunca se paga dos veces.</li>
 * </ol>
 *
 * <p><b>El cerrojo va en ESTA busqueda, y no mas abajo</b> (hallazgo de seguridad del
 * 2026-09-21). Antes esta clase materializaba el registro con la consulta sin cerrojo y
 * confiaba en que el {@code findByIdParaEscritura} de {@code RegistroService.requireRegistro}
 * serializara la carrera. No lo hacia: las dos lecturas viven en la MISMA transaccion —la que
 * abre el {@code @ApplicationModuleListener}— y por lo tanto en el mismo contexto de
 * persistencia, y Hibernate no rehidrata una entidad ya gestionada cuando la vuelve a traer una
 * consulta con cerrojo (el detalle, con los metodos, esta en el javadoc de
 * {@code SpringDataRegistroHabitoRepository.findByParticipanteHabitoYFechaParaEscritura}). El
 * segundo cierre concurrente decidia sobre el {@code PENDIENTE} viejo y volvia a pagar los
 * puntos; y como el comando de aca manda {@code respuestaTexto} y
 * {@code calificacionProductividad} nulos, ademas pisaba con {@code null} lo que el aprendiz
 * hubiera escrito por el camino HTTP. Con la lectura bloqueada aqui arriba no hay ninguna
 * lectura previa sin proteger, y la guarda de estado terminal decide sobre la fila que el
 * cerrojo acaba de leer.
 *
 * <p><b>Se completa con el gesto GENERICO a proposito</b>, no con
 * {@code GestoCompletar.PROPIO_DEL_HABITO}: asi {@link PoliticaPostDiarioComunidad} vuelve a
 * comprobar contra {@code publicaciones_muro} que la publicacion realmente existe dentro del dia
 * de ese registro. Es una verificacion barata y es la que sostiene el caso raro de la reentrega
 * tardia: si por lo que fuera el evento apuntara a un dia sin publicacion, la politica lo frena
 * en vez de regalar el habito. El habito no gana ningun atajo por venir de un evento.
 *
 * <p><b>Sin {@code @Transactional} propio</b>: quien lo invoca es un {@code @ApplicationModuleListener},
 * que ya corre en su propia transaccion (y con ella el {@code @Transactional} de
 * {@code RegistroService.completar}). Agregar otro aca no sumaria garantia y si escondería quien
 * es el dueno real de la transaccion.
 */
@Service
public class PostDiarioComunidadHabitoService implements CerrarPostDiarioComunidadUseCase {

    private static final Logger log = LoggerFactory.getLogger(PostDiarioComunidadHabitoService.class);

    private final LoadHabitoPort loadHabitoPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final CompletarRegistroUseCase completarRegistroUseCase;

    public PostDiarioComunidadHabitoService(LoadHabitoPort loadHabitoPort, LoadRegistroHabitoPort loadRegistroPort,
                                             ConsultarProgresoParticipanteHabitsPort progresoPort,
                                             CompletarRegistroUseCase completarRegistroUseCase) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadRegistroPort = loadRegistroPort;
        this.progresoPort = progresoPort;
        this.completarRegistroUseCase = completarRegistroUseCase;
    }

    @Override
    public void alPublicarEnElMuro(UserId autorId, Instant publicadoEn) {
        Optional<Habito> habito = loadHabitoPort.porClaveSistema(PoliticaPostDiarioComunidad.CLAVE_SISTEMA);
        if (habito.isEmpty()) {
            // El habito puede no estar en el catalogo de este entorno (fixtures, QA). No es motivo
            // para hacer ruido: publicar ya ocurrió y es lo que la persona vino a hacer.
            return;
        }
        Optional<ProgresoParticipanteHabits> progreso = progresoPort.deParticipante(autorId);
        if (progreso.isEmpty() || progreso.get().suspendido()) {
            // Publican tambien mentores y admins, que no tienen habitos: no es un error.
            return;
        }

        LocalDate diaDeLaPublicacion = publicadoEn.atZone(ZoneId.of(progreso.get().timezone())).toLocalDate();
        // CON cerrojo, y esta es la PRIMERA lectura de esta fila en la transaccion del oyente:
        // la guarda de abajo tiene que decidir sobre el estado que el cerrojo protege, no sobre
        // uno leido antes. Ver el javadoc de la clase.
        Optional<RegistroHabito> registro = loadRegistroPort
                .porParticipanteHabitoYFechaParaEscritura(autorId, habito.get().id(), diaDeLaPublicacion);
        if (registro.isEmpty()) {
            return; // no le toca ese dia, o lo pauso (D-87)
        }
        if (registro.get().estado().esTerminal()) {
            return; // ya cobrado (segunda publicacion del dia, reentrega del outbox o carrera), o expirado
        }

        completarRegistroUseCase.completar(
                new CompletarRegistroCommand(autorId, registro.get().id(), null, null));
        log.info("[habits] post diario en comunidad cerrado por publicacion del {} de {}", diaDeLaPublicacion,
                autorId);
    }
}
