package com.renaser.os.community.application.ports.out.celula;

import com.renaser.os.community.domain.model.celula.CelulaId;

import java.time.LocalDate;
import java.util.Optional;

public interface ConsultarRecepcionVigentePort {

    /**
     * El grupo de RECEPCION cuyo periodo contiene ese dia.
     *
     * <p><b>Si hay varios abiertos a la vez, gana el que empezo mas tarde.</b> No es un desempate
     * arbitrario: quien se registra hoy tiene que recibir la bienvenida completa, y el grupo mas
     * reciente es el que le deja mas dias por delante. Con el criterio contrario, alguien que
     * entra el ultimo dia de un grupo viejo se queda sin recepcion.
     *
     * <p>Vacio si no hay ninguno vigente. Eso NO es un error del sistema sino una tarea pendiente
     * del administrador, y quien llama lo trata como tal.
     */
    Optional<CelulaId> recepcionVigenteEn(LocalDate dia);
}
