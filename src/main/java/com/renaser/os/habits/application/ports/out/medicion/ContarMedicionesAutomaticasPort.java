package com.renaser.os.habits.application.ports.out.medicion;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;

/**
 * Quiénes tienen al menos un día medido por el teléfono (Health Connect) en un hábito del catálogo, desde su primer registro
 * hasta {@code hasta} inclusive (spec 037: la marca «auto» del ranking de km). En lote, como los otros del ranking (D-43).
 */
public interface ContarMedicionesAutomaticasPort {

    Set<UserId> conAlgunaMedicionAutomatica(Collection<UserId> participantes, String claveSistema, LocalDate hasta);

    /**
     * Quiénes tienen al menos un día medido por el teléfono, por Health Connect o por carreras con GPS (spec 042, FR-028: la marca
     * «auto» del ranking de km incluye a quien corrió con GPS). {@link #conAlgunaMedicionAutomatica} sigue siendo solo Health
     * Connect: SER la usa para saber si la persona tiene los pasos conectados.
     */
    Set<UserId> conAlgunaMedicionDelTelefono(Collection<UserId> participantes, String claveSistema, LocalDate hasta);
}
