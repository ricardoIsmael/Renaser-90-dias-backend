package com.renaser.os.onboarding.application.ports.out.respuesta;

import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadRespuestaPort {

    Optional<Respuesta> porUsuarioYPregunta(UserId usuarioId, int preguntaId);

    List<Respuesta> todasDeUsuario(UserId usuarioId);
}
