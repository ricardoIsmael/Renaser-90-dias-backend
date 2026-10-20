package com.renaser.os.points.domain.model.semaforo.mensaje;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * Elige el mensaje del día entre los de un grupo (FR-003, FR-004, FR-005). La regla, en orden:
 * <ol>
 *   <li>Solo cuentan los mensajes activos que el canal admite ({@link CanalDelMensaje}: el chat no dice {@code {faltan}})
 *       y cuyas variables la persona tiene: nunca se elige uno que pida un dato que falta.</li>
 *   <li>Se descarta, sin excepción, lo que el OTRO canal le mostró hoy (D-281): el mensaje del chat es distinto del de
 *       Hoy ese día y al revés. Si no queda ninguno, no hay mensaje.</li>
 *   <li>Se descartan los que vio en los últimos 14 días, por cualquier canal. Si ya vio todos, se descarta solo el de
 *       ayer. Si ese era el único posible, se vuelve a elegir entre todos.</li>
 *   <li>Entre los que quedan se elige uno al azar, uniforme.</li>
 * </ol>
 * Si en el grupo no queda ninguno con datos (ni siquiera uno sin variables) no hay mensaje: no se inventa uno.
 *
 * <p>El azar entra de afuera ({@code azar.applyAsInt(n)} devuelve un índice en {@code [0, n)}): el dominio es
 * determinista y se prueba con un azar fijo. Los candidatos se toman en el orden en que llegan, así que quien los
 * trae los ordena (por id) para que el índice signifique lo mismo siempre.
 */
public final class EleccionDelMensaje {

    private final IntUnaryOperator azar;
    private final CanalDelMensaje canal;

    /**
     * @param canal el canal por el que va a salir el mensaje: decide qué mensajes admite
     */
    public EleccionDelMensaje(IntUnaryOperator azar, CanalDelMensaje canal) {
        this.azar = Objects.requireNonNull(azar, "azar es obligatorio");
        this.canal = Objects.requireNonNull(canal, "canal es obligatorio");
    }

    public Optional<MensajeDelSemaforo> elegir(List<MensajeDelSemaforo> delGrupo, DatosDelMensaje datos,
                                                MensajesVistos vistos) {
        List<MensajeDelSemaforo> posibles = delGrupo.stream()
                .filter(MensajeDelSemaforo::activo)
                .filter(canal::admite)
                .filter(mensaje -> datos.tieneTodas(mensaje.variables()))
                .filter(mensaje -> !vistos.hoyEnElOtroCanal().contains(mensaje.id()))
                .toList();
        if (posibles.isEmpty()) {
            return Optional.empty();
        }
        List<MensajeDelSemaforo> sinRepetir = sinRepetir(posibles, vistos);
        return Optional.of(sinRepetir.get(Objects.checkIndex(azar.applyAsInt(sinRepetir.size()), sinRepetir.size())));
    }

    private static List<MensajeDelSemaforo> sinRepetir(List<MensajeDelSemaforo> posibles, MensajesVistos vistos) {
        List<MensajeDelSemaforo> nuevos = quitando(posibles, vistos.enLaVentana()::contains);
        if (!nuevos.isEmpty()) {
            return nuevos;
        }
        List<MensajeDelSemaforo> sinElDeAyer = quitando(posibles, vistos.deAyer()::contains);
        return sinElDeAyer.isEmpty() ? posibles : sinElDeAyer;
    }

    private static List<MensajeDelSemaforo> quitando(List<MensajeDelSemaforo> mensajes, Predicate<String> porId) {
        return mensajes.stream().filter(mensaje -> !porId.test(mensaje.id())).toList();
    }
}
