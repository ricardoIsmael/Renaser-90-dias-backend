package com.renaser.os.users.application.ports.out.formacion;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.formacion.ParticipacionAnterior;

import java.util.List;

/** El historial de las formaciones que una persona ya cursó (tabla {@code participaciones_anteriores}, V127). Solo se agrega. */
public interface ParticipacionesAnterioresPort {

    void guardar(ParticipacionAnterior anterior);

    /** Las más recientes primero. */
    List<ParticipacionAnterior> de(UserId persona);
}
