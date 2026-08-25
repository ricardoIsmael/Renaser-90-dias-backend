package com.renaser.os.habits.application.ports.out.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;

import java.util.Optional;

public interface LoadSesionBloqueoPort {

    Optional<SesionBloqueo> porRegistro(RegistroHabitoId registroHabitoId);
}
