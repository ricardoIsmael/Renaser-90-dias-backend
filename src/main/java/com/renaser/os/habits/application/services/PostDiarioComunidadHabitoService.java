package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.ports.in.registro.CerrarPostDiarioComunidadUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
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
 * <p><b>Si el registro de ese dia todavia no existe, se genera</b> (E-438, 2026-09-29). Los
 * registros del dia nacen en el barrido de las 05:02 UTC o, si ese barrido no alcanzo a la
 * persona, recien cuando la app pide {@code GET /habit-tracks/today}. Quien publicaba ANTES de que
 * existiera su registro —cuenta activada ese mismo dia, backend caido a la medianoche, zona al
 * oeste de Lima donde el barrido cae el dia anterior— se quedaba con el habito PENDIENTE para
 * siempre: este oyente no encontraba nada que cerrar, y el registro que se generaba despues ya no
 * tenia quien lo cerrara. Lo veia asi la ficha del administrador y la del mentor, con el post
 * publicado. Se genera SOLO si el dia de la publicacion es HOY en su zona: una reentrega tardia
 * del outbox no puede fabricar la jornada entera de un dia pasado. Generar es idempotente
 * ({@code insertarSiNoExiste}), asi que competir con el {@code GET /today} del mismo telefono no
 * duplica nada.
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
    private final GenerarTracksDelDiaUseCase generarTracksUseCase;
    private final Clock clock;

    public PostDiarioComunidadHabitoService(LoadHabitoPort loadHabitoPort, LoadRegistroHabitoPort loadRegistroPort,
                                             ConsultarProgresoParticipanteHabitsPort progresoPort,
                                             CompletarRegistroUseCase completarRegistroUseCase,
                                             GenerarTracksDelDiaUseCase generarTracksUseCase, Clock clock) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadRegistroPort = loadRegistroPort;
        this.progresoPort = progresoPort;
        this.completarRegistroUseCase = completarRegistroUseCase;
        this.generarTracksUseCase = generarTracksUseCase;
        this.clock = clock;
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

        ZoneId zona = ZoneId.of(progreso.get().timezone());
        LocalDate diaDeLaPublicacion = publicadoEn.atZone(zona).toLocalDate();
        Optional<RegistroHabito> registro = registroDelDia(autorId, habito.get(), diaDeLaPublicacion, zona);
        if (registro.isEmpty()) {
            return; // no le toca ese dia, o lo pauso (D-87)
        }
        if (registro.get().estado().esTerminal()) {
            return; // ya cobrado (segunda publicacion del dia, reentrega del outbox o carrera), o expirado
        }
        if (registro.get().suDiaYaTermino(zona, clock.now())) {
            // D-259: un habito de un dia que ya termino no se registra, y el caso de uso lo rechazaria con 409. Sin
            // esta guarda, una reentrega del outbox pasada la medianoche fallaria en cada reintento. Es el mismo
            // final que cuando el barrido de expiracion llega primero (arriba, EXPIRADO es terminal).
            log.info("[habits] post diario del {} de {} llego con ese dia ya cerrado: no se registra", diaDeLaPublicacion,
                    autorId);
            return;
        }

        completarRegistroUseCase.completar(
                new CompletarRegistroCommand(autorId, registro.get().id(), null, null));
        log.info("[habits] post diario en comunidad cerrado por publicacion del {} de {}", diaDeLaPublicacion,
                autorId);
    }

    /**
     * El registro de ese dia, CON cerrojo, generando la jornada si todavia no existia (ver el
     * javadoc de la clase). Las dos lecturas son con cerrojo y ninguna va sin el: la guarda de
     * estado terminal tiene que decidir sobre la fila que el cerrojo protege.
     */
    private Optional<RegistroHabito> registroDelDia(UserId autorId, Habito habito, LocalDate dia, ZoneId zona) {
        Optional<RegistroHabito> registro =
                loadRegistroPort.porParticipanteHabitoYFechaParaEscritura(autorId, habito.id(), dia);
        if (registro.isPresent() || !dia.equals(clock.now().atZone(zona).toLocalDate())) {
            return registro;
        }
        generarTracksUseCase.generarDiaCompletoEnSuZona(autorId);
        return loadRegistroPort.porParticipanteHabitoYFechaParaEscritura(autorId, habito.id(), dia);
    }
}
