package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo.mensaje;

import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeDelSemaforo;

import java.util.List;

/** De dónde sale el catálogo completo cada vez que la copia en memoria hay que armarla. */
interface LectorDelCatalogo {

    /** Todas las filas válidas, activas o no. Una con el texto mal escrito se salta, no tumba a las demás. */
    List<MensajeDelSemaforo> todos();
}
