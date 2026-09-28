package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.onboarding.application.ports.in.caja.ContenidoDeCajaUseCase;
import com.renaser.os.onboarding.application.ports.out.caja.ContenidoDeCajaPort;
import com.renaser.os.onboarding.domain.model.caja.ContenidoDeCaja;
import com.renaser.os.onboarding.domain.model.caja.ElementoDeCaja;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * La lista de lo que lleva la caja (D-219): el Admin la reescribe entera. Un checklist ya marcado no se toca;
 * si se suma un elemento, a las cajas en armado les falta marcarlo antes de enviarlas.
 */
@Service
public class ContenidoDeCajaService implements ContenidoDeCajaUseCase {

    private final GuardiaDeCaja guardia;
    private final ContenidoDeCajaPort contenido;

    ContenidoDeCajaService(GuardiaDeCaja guardia, ContenidoDeCajaPort contenido) {
        this.guardia = guardia;
        this.contenido = contenido;
    }

    @Override
    public ContenidoDeCaja ver(UserId actorId) {
        guardia.exigirAdmin(actorId);
        return contenido.vigente();
    }

    @Override
    @Transactional
    public ContenidoDeCaja reemplazar(UserId actorId, List<PedidoDeElemento> elementos) {
        guardia.exigirAdmin(actorId);
        ContenidoDeCaja nuevo = ContenidoDeCaja.nueva(elementos == null ? null : elementos.stream()
                .map(e -> e == null ? ElementoDeCaja.de(null, null) : ElementoDeCaja.de(e.valor(), e.etiqueta()))
                .toList());
        contenido.reemplazar(nuevo);
        return contenido.vigente();
    }
}
