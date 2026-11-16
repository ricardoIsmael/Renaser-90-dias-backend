package com.renaser.os.shared.application.ports.out;

import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.SujetoDeLimite;

/**
 * Deja, de forma durable, quién habría sido bloqueado en el grupo y cuántas veces ese día (spec 050, «Cómo funciona solo registrar»):
 * el registro de Docker solo vive desde el último despliegue. Solo guarda el id de la cuenta, nunca una IP ni datos personales.
 */
public interface AnotarCuentaQueHabriaBloqueadoPort {

    void anotar(GrupoDeLimite grupo, SujetoDeLimite sujeto);
}
