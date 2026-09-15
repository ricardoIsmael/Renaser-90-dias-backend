package com.renaser.os.points.application.ports.in.ranking;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Rehacer el <b>corte</b> del ranking a pedido de un operador (2026-09-15, D-129).
 *
 * <p><b>Que es el corte.</b> El ranking no se calcula cuando alguien abre la pantalla: se congela
 * una vez al dia ({@code SnapshotRankingScheduler}, 05:05 UTC) y todos ven la misma tabla durante
 * toda la jornada. Sin eso, las posiciones bailarian cada vez que cualquiera marca un habito, y
 * dos personas mirando al mismo tiempo verian tablas distintas.
 *
 * <p><b>Por que hace falta poder pedirlo a mano.</b> El cron corre una vez al dia y solo si el
 * backend esta arriba a esa hora exacta. Si el servidor estuvo caido —o recien se desplego un
 * cambio en como se calcula, como paso con la coherencia (D-128)— la tabla del dia queda vieja o
 * directamente no existe, y no hay forma de arreglarla hasta el dia siguiente.
 *
 * <p>NO es un caso de uso distinto del que corre el cron: genera lo mismo, con el mismo codigo.
 * Lo unico que agrega es <b>quien puede pedirlo</b>.
 */
public interface RegenerarSnapshotsRankingUseCase {

    ResultadoRegeneracion regenerar(RegenerarSnapshotsCommand command);

    /**
     * @param actorId quien lo pide — se verifica que sea ADMIN/ALCHEMIST activo, igual que el
     *                ajuste manual de puntos
     * @param fecha   el dia del corte. {@code null} = hoy
     */
    record RegenerarSnapshotsCommand(UserId actorId, LocalDate fecha) {
        public RegenerarSnapshotsCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
        }
    }

    /**
     * @param fecha    el dia que quedo regenerado
     * @param tipos    los tipos que se generaron bien
     * @param fallados los que fallaron, con su motivo — un tipo que falla NO tumba a los otros,
     *                 mismo criterio best-effort que el cron
     */
    record ResultadoRegeneracion(LocalDate fecha, List<String> tipos, List<String> fallados) {
    }
}
