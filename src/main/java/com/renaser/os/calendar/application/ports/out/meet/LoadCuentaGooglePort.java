package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;

import java.util.Optional;

public interface LoadCuentaGooglePort {

    /** La única cuenta de Google de la plataforma; vacío si nunca se conectó o se desconectó. */
    Optional<CuentaDeGoogle> cargar();
}
