package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;

import java.util.List;

/** Los desvíos guardados de todas las funcionalidades, con sus excepciones por rol. */
public interface CargarEstadosPort {

    List<EstadoDeFuncionalidad> todos();
}
