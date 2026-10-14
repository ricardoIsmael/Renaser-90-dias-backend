package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;

import java.util.List;
import java.util.Optional;

/** Los despliegues en curso (V103), con sus probadores. Sin fila, la funcionalidad no está en despliegue. */
public interface CargarDesplieguesPort {

    /** Todos: la copia en memoria se arma con esto cada 30 s y en cada cambio. */
    List<DespliegueDeFuncionalidad> todos();

    Optional<DespliegueDeFuncionalidad> de(String clave);
}
