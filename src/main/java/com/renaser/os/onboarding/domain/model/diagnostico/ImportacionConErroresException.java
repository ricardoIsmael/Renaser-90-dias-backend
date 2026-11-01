package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.ErrorDeImportacion;

import java.util.List;

/** El archivo trae errores: no se importa nada (todo o nada). 400 con los errores por fila. */
public class ImportacionConErroresException extends IllegalArgumentException {

    public static final String CODIGO = "IMPORTACION_CON_ERRORES";

    private final transient List<ErrorDeImportacion> errores;

    public ImportacionConErroresException(List<ErrorDeImportacion> errores) {
        super("El archivo tiene " + errores.size() + (errores.size() == 1 ? " error" : " errores") + ": corrígelos y vuelve a subirlo");
        this.errores = List.copyOf(errores);
    }

    public List<ErrorDeImportacion> errores() {
        return errores;
    }
}
