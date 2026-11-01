package com.renaser.os.onboarding.application.ports.out.pagos;

import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.UUID;

/** Las cuotas del cronograma de cada persona (spec 032, tabla {@code cuotas_de_pago}). Cada cuota es de UNA persona: nadie lee ni toca las de otra. */
public interface CuotasDePagoPort {

    /** Las de la persona, de la fecha más cercana a la más lejana. */
    List<Cuota> deLaPersona(UserId persona);

    /** Crea la cuota o guarda su cambio (fecha, monto o marca). */
    void guardar(UserId persona, Cuota cuota);

    /** Quita una cuota de esa persona; si no es suya, no hace nada. */
    void eliminar(UserId persona, UUID cuota);

    /** Quita todas las de la persona. */
    void eliminarTodas(UserId persona);
}
