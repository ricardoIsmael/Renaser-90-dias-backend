package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.carrera.BorrarRutasViejasUseCase;
import com.renaser.os.habits.application.ports.out.carrera.RetencionDeRutasPort;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Borra las rutas de más de {@link #RETENCION} (spec 042, FR-042, decisión del dueño). La regla se deriva del instante en que
 * empezó la carrera, no del día local de nadie: un día de más o de menos no cambia nada que alguien vea, así que no depende de la
 * zona (regla 02). Pagina ({@link #TAMANO_LOTE}) y cada lote va en su propia transacción (la del adaptador): un fallo a mitad no
 * deshace lo ya borrado, y la corrida siguiente se pone al día sola.
 */
@Service
class RetencionDeRutasService implements BorrarRutasViejasUseCase {

    static final Duration RETENCION = Duration.ofDays(180);
    static final int TAMANO_LOTE = 500;

    private final RetencionDeRutasPort retencion;
    private final Clock clock;

    RetencionDeRutasService(RetencionDeRutasPort retencion, Clock clock) {
        this.retencion = retencion;
        this.clock = clock;
    }

    @Override
    public int borrarRutasViejas() {
        Instant limite = clock.now().minus(RETENCION);
        int total = 0;
        int borradas;
        do {
            borradas = retencion.borrarRutasAnterioresA(limite, TAMANO_LOTE);
            total += borradas;
        } while (borradas == TAMANO_LOTE);
        return total;
    }
}
