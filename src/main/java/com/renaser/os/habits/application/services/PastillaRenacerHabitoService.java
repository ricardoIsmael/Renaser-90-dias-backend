package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Implementa {@link CompletarPastillaRenacerUseCase} delegando en
 * {@link CompletarRegistroUseCase} el calculo de puntos, la ventana de entrega y el evento de
 * dominio — ese calculo vive en un solo lugar ({@code RegistroService}, ver el javadoc de
 * {@code PoliticaHabito}: "una politica decide SI una accion procede y por que; nunca
 * reimplementa lo compartido"). Esta clase solo localiza el track de HOY del habito
 * {@code PASTILLA_RENACER}.
 *
 * <p>Copia deliberada de la forma de {@code ClaseDiariaHabitoService}: son el mismo problema
 * (un modulo que sabe que ocurrio un hecho y necesita cerrar el habito que le corresponde, sin
 * manejar {@code RegistroHabitoId} ajenos). Se prefirio repetir esa forma antes que
 * generalizarla en un "completar por clave", por el motivo documentado en el javadoc del
 * puerto.
 *
 * <p><b>Diferencia con Clase Diaria:</b> ahi la ausencia del track de hoy es un error del
 * llamador (400/404); aca es un caso normal — el aprendiz puede tener "Pastilla Renacer"
 * pausado (D-87) o el habito puede no haberse generado hoy, y su entrega del resumen de
 * Espiritu sigue siendo valida igual. Por eso devuelve {@link Optional#empty()} en vez de
 * lanzar.
 *
 * <p><b>El track de hoy se busca CON cerrojo</b> (hallazgo de seguridad del 2026-09-21, el mismo
 * que ya se arreglo en {@code PostDiarioComunidadHabitoService} y en
 * {@code ClaseDiariaHabitoService}). Antes se buscaba con la consulta que no bloquea y se
 * confiaba en que el {@code findByIdParaEscritura} de {@code RegistroService.requireRegistro}
 * serializara la carrera; no lo hacia, porque Hibernate no rehidrata una entidad ya gestionada
 * cuando una consulta con cerrojo la vuelve a traer — el detalle, con los metodos, esta en el
 * javadoc de
 * {@code SpringDataRegistroHabitoRepository.findByParticipanteHabitoYFechaParaEscritura}.
 *
 * <p><b>Que el llamador use REQUIRES_NEW no cambiaba nada, y conviene dejarlo escrito</b> porque
 * invita a pensar que si. {@code EspirituService.reflejarEnPastillaRenacer} abre una transaccion
 * propia, pero lo que aisla es la entrega del resumen de Espiritu de un fallo de este habito: la
 * transaccion nueva es una sola, y las DOS lecturas del registro —la de aca y la de
 * {@code RegistroService.completar}, que es {@code @Transactional} REQUIRED y se une a ella—
 * caen dentro de esa misma transaccion nueva y comparten su contexto de persistencia. El
 * REQUIRES_NEW mueve de lugar la transaccion que las dos lecturas comparten; no las separa.
 *
 * <p>Lo que costaba: este habito NO tiene una politica que le cierre la ruta generica (no existe
 * una {@code PoliticaPastillaRenacer}, a diferencia de {@code PoliticaClaseDiaria}), asi que hay
 * dos caminos vivos que lo cierran — la entrega del resumen de Espiritu y el
 * {@code POST /habit-tracks/{id}/complete} de siempre. Simultaneos, los dos leian
 * {@code PENDIENTE} y los dos pagaban; y como el camino de Espiritu manda su propio resumen,
 * el segundo en escribir pisaba el texto del primero. Con la lectura bloqueada aqui arriba el
 * segundo entra serializado, lee {@code COMPLETADO} y devuelve el resultado ya otorgado.
 *
 * <p><b>Tomar el cerrojo unas lineas antes no reabre el auto-interbloqueo</b> que advierte el
 * javadoc de {@code RegistroService.transaccionPropia} y que
 * {@code EspirituService.reflejarEnPastillaRenacer} ya descarta: el cerrojo sigue cayendo DENTRO
 * de la transaccion anidada y sobre {@code registros_habito}, una tabla que la transaccion
 * suspendida (la de {@code registros_espiritu}) no leyo ni bloqueo. No hay fila en comun, y la
 * ventana del cerrojo crece lo que ocupa una sola comparacion de estado.
 */
@Service
public class PastillaRenacerHabitoService implements CompletarPastillaRenacerUseCase {

    private final LoadHabitoPort loadHabitoPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final CompletarRegistroUseCase completarRegistroUseCase;
    private final Clock clock;

    public PastillaRenacerHabitoService(LoadHabitoPort loadHabitoPort, LoadRegistroHabitoPort loadRegistroPort,
                                         ConsultarProgresoParticipanteHabitsPort progresoPort,
                                         CompletarRegistroUseCase completarRegistroUseCase, Clock clock) {
        this.loadHabitoPort = loadHabitoPort;
        this.loadRegistroPort = loadRegistroPort;
        this.progresoPort = progresoPort;
        this.completarRegistroUseCase = completarRegistroUseCase;
        this.clock = clock;
    }

    @Override
    public Optional<RegistroCompletado> completarDeHoy(UserId participanteId, String resumen) {
        Optional<Habito> habito = loadHabitoPort.porClaveSistema(CLAVE_SISTEMA_PASTILLA_RENACER);
        if (habito.isEmpty()) {
            // El habito puede estar fuera del catalogo de este entorno (fixtures, QA): no es
            // motivo para tumbar la entrega del resumen, que es lo que el aprendiz vino a hacer.
            return Optional.empty();
        }

        LocalDate hoy = fechaHoyEnZonaDe(requireProgresoNoSuspendido(participanteId).timezone());
        // CON cerrojo, y esta es la PRIMERA lectura de esta fila en la transaccion anidada que
        // abre EspirituService: la guarda de abajo y el completar() que la sigue tienen que
        // decidir sobre el estado que el cerrojo protege. Ver el javadoc de la clase.
        Optional<RegistroHabito> registro = loadRegistroPort
                .porParticipanteHabitoYFechaParaEscritura(participanteId, habito.get().id(), hoy);
        if (registro.isEmpty()) {
            return Optional.empty();
        }

        RegistroHabito track = registro.get();
        if (track.estado() == EstadoRegistro.COMPLETADO) {
            return Optional.of(new RegistroCompletado(track.id().value(), track.puntosOtorgados()));
        }

        RegistroHabito completado = completarRegistroUseCase.completar(
                new CompletarRegistroCommand(participanteId, track.id(), resumen, null));
        return Optional.of(new RegistroCompletado(completado.id().value(), completado.puntosOtorgados()));
    }

    /**
     * Defensa propia, mismo criterio que {@code ClaseDiariaHabitoService}: no se confia en que
     * todo llamador futuro haya validado la suspension antes de llegar aca.
     */
    private ProgresoParticipanteHabits requireProgresoNoSuspendido(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }

    /** Mismo criterio que {@code RachaService.fechaHoyDe}: reloj inyectado, nunca el del sistema. */
    private LocalDate fechaHoyEnZonaDe(String timezone) {
        return clock.now().atZone(ZoneId.of(timezone)).toLocalDate();
    }
}
