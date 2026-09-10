package com.renaser.os.community.application.ports.out.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;

public interface SavePoliticaMentoriaPort {

    PoliticaMentoria save(PoliticaMentoria politica);
}
