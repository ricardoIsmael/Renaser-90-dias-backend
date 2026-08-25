package com.renaser.os.community.application.ports.out.cohorte;

import com.renaser.os.community.domain.model.cohorte.Cohorte;

public interface SaveCohortePort {

    Cohorte save(Cohorte cohorte);
}
