package com.renaser.os.habits.application.ports.out.santuario;

import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;

public interface SaveSesionBloqueoPort {

    SesionBloqueo save(SesionBloqueo sesion);
}
