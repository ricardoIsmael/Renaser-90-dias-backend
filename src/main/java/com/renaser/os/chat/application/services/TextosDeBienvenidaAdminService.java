package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.CambiarTextoDeBienvenidaUseCase;
import com.renaser.os.chat.application.ports.in.bienvenida.VerBienvenidaUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.EstadoDePieza;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

/**
 * Ver la bienvenida y cambiar sus mensajes desde la app (D-210, pedido del dueño del 2026-09-27).
 *
 * <p>Lo guardado reemplaza al texto de {@code bienvenida/mensajes.yaml} desde la PRÓXIMA bienvenida: los
 * servicios que la mandan leen los textos vigentes en cada envío ({@code TextosDeBienvenidaVigentes}).
 * Lo que ya se mandó no cambia.
 *
 * <p>Sin {@code @Transactional}: cada cambio es un solo INSERT en la bitácora. Si dos personas guardan a
 * la vez, quedan las dos filas y vale la última.
 */
@Service
public class TextosDeBienvenidaAdminService implements VerBienvenidaUseCase, CambiarTextoDeBienvenidaUseCase {

    private final BienvenidaParaAdministrar bienvenida;
    private final CambiosDeBienvenidaPort cambios;
    private final Clock clock;

    TextosDeBienvenidaAdminService(BienvenidaParaAdministrar bienvenida, CambiosDeBienvenidaPort cambios,
                                   Clock clock) {
        this.bienvenida = bienvenida;
        this.cambios = cambios;
        this.clock = clock;
    }

    @Override
    public BienvenidaEditable ver(UserId actorId) {
        bienvenida.exigirQuePuedaCambiarla(actorId);
        return bienvenida.leer();
    }

    @Override
    public BienvenidaEditable cambiar(UserId actorId, String clave, String texto) {
        bienvenida.exigirQuePuedaCambiarla(actorId);
        PiezaDeBienvenida pieza = PiezaDeBienvenida.textoDe(clave);
        CambioDeBienvenida cambio = CambioDeBienvenida.texto(pieza, texto, actorId, clock.now());
        if (!cambio.valor().equals(bienvenida.estado(pieza).vigente())) {
            cambios.registrar(cambio);
        }
        return bienvenida.leer();
    }

    @Override
    public BienvenidaEditable volverAlOriginal(UserId actorId, String clave) {
        bienvenida.exigirQuePuedaCambiarla(actorId);
        PiezaDeBienvenida pieza = PiezaDeBienvenida.textoDe(clave);
        EstadoDePieza estado = bienvenida.estado(pieza);
        if (estado.cambiada()) {
            cambios.registrar(CambioDeBienvenida.volverAlOriginal(pieza, actorId, clock.now()));
        }
        return bienvenida.leer();
    }
}
