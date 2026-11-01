package com.renaser.os.habits.application.ports.out.categoria;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Map;

/** Deja constancia de quien cambio que en el catalogo de una formacion (spec 023, FR-024). Solo se agrega: nunca se corrige una fila. */
public interface RegistrarCambioDelCatalogoPort {

    void registrar(CambioDelCatalogo cambio);

    enum Entidad { CATEGORIA, HABITO }

    enum Accion { CREADA, RENOMBRADA, ICONO, ORDENADA, ACTIVADA, DESACTIVADA, ELIMINADA, COPIADO }

    /** {@code detalle} lleva el antes y el despues (texto simple); puede ir vacio. */
    record CambioDelCatalogo(Formacion formacion, Entidad entidad, String clave, Accion accion, UserId actor,
                             Map<String, String> detalle, Instant cuando) {
    }
}
