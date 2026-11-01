package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.CumplimientoDelDiaFinder;
import com.renaser.os.habits.application.ports.out.registro.LeerRegistrosDelDiaPort;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Delega en el puerto: la regla (qué registros cuentan) vive en la consulta. Sin personas o sin formaciones, no se lee nada. */
@Service
public class CumplimientoDelDiaFinderService implements CumplimientoDelDiaFinder {

    private final LeerRegistrosDelDiaPort lecturas;

    public CumplimientoDelDiaFinderService(LeerRegistrosDelDiaPort lecturas) {
        this.lecturas = lecturas;
    }

    @Override
    public List<RegistroDelDia> deVariasPersonas(Collection<UserId> personas, LocalDate fecha, AlcanceDeFormacion alcance) {
        if (personas.isEmpty() || alcance.esVacio()) {
            return List.of();
        }
        return lecturas.deVariasPersonas(personas, fecha, alcance);
    }
}
