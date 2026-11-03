package com.renaser.os.onboarding.application.ports.in.diagnostico;

import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion;
import com.renaser.os.shared.domain.UserId;

/** Cargar preguntas desde una hoja de cálculo pegada como texto (spec 026, US6): revisar sin guardar y aplicar todo o nada. */
public interface ImportarDiagnosticoUseCase {

    enum Modo {
        ANADIR,
        REEMPLAZAR
    }

    /** No guarda nada. {@code preguntasQueSeRetirarian}: lo que quedaría fuera si se reemplaza la ficha; {@code conRespuestas}: de ésas, las que ya respondió alguien. */
    VistaPreviaDeImportacion revisar(UserId actor, String texto, Modo modo);

    /** Aplica el archivo. Si trae un solo error no guarda nada ({@code ImportacionConErroresException}). Las preguntas quedan en el borrador. */
    ResultadoDeImportacion importar(UserId actor, String texto, Modo modo);

    record VistaPreviaDeImportacion(LecturaDeImportacion lectura, int preguntasQueSeRetirarian, int conRespuestas) {
    }

    record ResultadoDeImportacion(int secciones, int preguntas, int eliminadas, int retiradas) {
    }
}
