package com.renaser.os.evidence.application.ports.out.evidencia;

import com.renaser.os.evidence.domain.model.evidencia.Evidencia;

public interface SaveEvidenciaPort {

    Evidencia save(Evidencia evidencia);
}
