package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.meet.DisponibilidadDeMeet;
import com.renaser.os.calendar.application.ports.out.meet.ConfiguracionDeGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleNoConectadaException;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Si se pueden crear salas de Meet ahora (spec 040, FR-014): el servidor tiene sus credenciales, hay una cuenta de Google activa y Soporte TI prendió
 * {@code eventos.meet} (apagada de fábrica). Es la única respuesta a esa pregunta: la usan el formulario (para dibujar la opción) y el guardado (para
 * rechazar con 404 o 409 lo que el formulario ya no ofrece).
 */
@Component
class DisponibilidadDeLasSalas {

    static final String CLAVE_DEL_INTERRUPTOR = "eventos.meet";
    static final String APAGADA = "APAGADA";
    static final String SIN_CONFIGURAR = "SIN_CONFIGURAR";
    static final String SIN_CUENTA = "SIN_CUENTA";
    static final String CUENTA_REVOCADA = "CUENTA_REVOCADA";

    private final Interruptores interruptores;
    private final ConfiguracionDeGooglePort configuracion;
    private final LoadCuentaGooglePort loadCuentaPort;

    DisponibilidadDeLasSalas(Interruptores interruptores, ConfiguracionDeGooglePort configuracion,
                             LoadCuentaGooglePort loadCuentaPort) {
        this.interruptores = interruptores;
        this.configuracion = configuracion;
        this.loadCuentaPort = loadCuentaPort;
    }

    DisponibilidadDeMeet de(UserId actor) {
        if (!interruptores.prendidaPara(CLAVE_DEL_INTERRUPTOR, actor)) {
            return DisponibilidadDeMeet.no(APAGADA);
        }
        return motivoDeLaCuenta().map(DisponibilidadDeMeet::no).orElseGet(DisponibilidadDeMeet::siPuede);
    }

    /**
     * Rechaza lo que el formulario no ofrecería. Apagada por Soporte TI: 404 {@code FUNCIONALIDAD_NO_DISPONIBLE} (SC-009). Sin cuenta, sin
     * credenciales o revocada: 409 «Todavía no hay una cuenta de Google conectada.» (Story 1.4).
     */
    void exigir(UserId actor) {
        interruptores.exigirPrendidaPara(CLAVE_DEL_INTERRUPTOR, actor);
        if (motivoDeLaCuenta().isPresent()) {
            throw new CuentaGoogleNoConectadaException();
        }
    }

    private Optional<String> motivoDeLaCuenta() {
        if (!configuracion.estaConfigurada()) {
            return Optional.of(SIN_CONFIGURAR);
        }
        Optional<CuentaDeGoogle> cuenta = loadCuentaPort.cargar();
        if (cuenta.isEmpty()) {
            return Optional.of(SIN_CUENTA);
        }
        return cuenta.get().activa() ? Optional.empty() : Optional.of(CUENTA_REVOCADA);
    }
}
