package com.renaser.os.users.application.ports.out.ajustediaprograma;

import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;

/** Append-only (V21): solo se agregan ajustes, nunca se editan ni se borran. */
public interface SaveAjusteDiaProgramaPort {

    AjusteDiaPrograma save(AjusteDiaPrograma ajuste);
}
