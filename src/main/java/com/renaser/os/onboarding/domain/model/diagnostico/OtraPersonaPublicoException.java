package com.renaser.os.onboarding.domain.model.diagnostico;

/** Alguien publicó después de la versión que Administración cargó: 409, para no pisar lo del otro en silencio (FR-035). */
public class OtraPersonaPublicoException extends IllegalStateException {

    public static final String CODIGO = "OTRA_PERSONA_PUBLICO";

    private final int versionVigente;

    public OtraPersonaPublicoException(int versionVigente) {
        super("Otra persona publicó cambios mientras editabas");
        this.versionVigente = versionVigente;
    }

    public int versionVigente() {
        return versionVigente;
    }
}
