package com.renaser.os.habits.application.ports.out.audiencia;

import com.renaser.os.habits.domain.model.audiencia.VersionDeAudiencia;

/** Append-only: una versión nueva nunca reemplaza a la anterior (V96). */
public interface GuardarAudienciaPort {

    void agregarVersion(VersionDeAudiencia version);
}
