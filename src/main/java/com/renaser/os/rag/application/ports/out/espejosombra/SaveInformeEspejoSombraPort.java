package com.renaser.os.rag.application.ports.out.espejosombra;

import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;

public interface SaveInformeEspejoSombraPort {

    /** Inserta el informe y sus preguntas de confrontación. Un informe nunca se actualiza tras crearse. */
    InformeEspejoSombra save(InformeEspejoSombra informe);
}
