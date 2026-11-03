package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosOriginalesDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.EstadoDePieza;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.Formacion;
import org.springframework.stereotype.Component;

/**
 * Los textos que salen hoy en la bienvenida (D-210): el último que guardó Administración desde la app o,
 * si no guardó ninguno o volvió al original, el de {@code bienvenida/mensajes.yaml}. La regla es de
 * {@link EstadoDePieza}; acá solo se juntan las dos fuentes.
 *
 * <p><b>De la formación de quien recibe la bienvenida</b> (spec 035): el último cambio de ESA formación o su original. Lo que Administración editó en
 * otra formación nunca llega acá.
 *
 * <p>Se lee la bitácora en cada pedido, sin guardar nada: una bienvenida sale cuando nace un soporte o
 * cuando alguien se suma a un grupo, y así un cambio vale para la próxima sin esperar ni reiniciar.
 */
@Component
class TextosDeBienvenidaVigentes implements TextosDeBienvenidaPort {

    private final TextosOriginalesDeBienvenidaPort originales;
    private final CambiosDeBienvenidaPort cambios;

    TextosDeBienvenidaVigentes(TextosOriginalesDeBienvenidaPort originales, CambiosDeBienvenidaPort cambios) {
        this.originales = originales;
        this.cambios = cambios;
    }

    @Override
    public String soporteConLaTarjeta(Formacion formacion) {
        return vigente(formacion, PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA);
    }

    @Override
    public String soporteFormal(Formacion formacion) {
        return vigente(formacion, PiezaDeBienvenida.SOPORTE_FORMAL);
    }

    @Override
    public String grupo(Formacion formacion) {
        return vigente(formacion, PiezaDeBienvenida.GRUPO);
    }

    private String vigente(Formacion formacion, PiezaDeBienvenida pieza) {
        return new EstadoDePieza(pieza, originales.original(formacion, pieza), cambios.ultimo(formacion, pieza).orElse(null)).vigente();
    }
}
