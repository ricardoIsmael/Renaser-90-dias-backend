package com.renaser.os.phasecontracts.domain.model.contrato;

public enum FasePrograma {

    FASE_1_RENACER(1, 1, null, "Fase I · El Renacimiento"),
    FASE_2_DESARROLLO(2, 8, 17, "Fase II · El Desarrollo"),
    FASE_3_GUERRERO_ALQUIMISTA(3, 35, 35, "Fase III · El Guerrero Alquimista"),
    FASE_4_ASCENSION(4, 65, 65, "Fase IV · El Ascenso");

    private final int numero;
    private final int diaInicio;
    private final Integer diaDesbloqueoFirma;
    private final String etiqueta;

    FasePrograma(int numero, int diaInicio, Integer diaDesbloqueoFirma, String etiqueta) {
        this.numero = numero;
        this.diaInicio = diaInicio;
        this.diaDesbloqueoFirma = diaDesbloqueoFirma;
        this.etiqueta = etiqueta;
    }

    /** 1..4 — usado para nombrar la ruta de la firma (fase_{numero}.svg), nunca el ordinal del enum. */
    public int numero() {
        return numero;
    }

    /** Dia de programa a partir del cual corresponde firmar, o null si esta fase no se firma aqui (Fase I). */
    public Integer diaDesbloqueoFirma() {
        return diaDesbloqueoFirma;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public static FasePrograma paraDiaPrograma(int diaPrograma) {
        if (diaPrograma >= FASE_4_ASCENSION.diaInicio) {
            return FASE_4_ASCENSION;
        }
        if (diaPrograma >= FASE_3_GUERRERO_ALQUIMISTA.diaInicio) {
            return FASE_3_GUERRERO_ALQUIMISTA;
        }
        if (diaPrograma >= FASE_2_DESARROLLO.diaInicio) {
            return FASE_2_DESARROLLO;
        }
        return FASE_1_RENACER;
    }

    public boolean firmaDesbloqueadaEnDia(int diaProgramaActual) {
        return diaDesbloqueoFirma != null && diaProgramaActual >= diaDesbloqueoFirma;
    }

    /**
     * La fase que le corresponde firmar HOY a un participante en ese dia de programa,
     * o null si no hay ninguna pendiente de desbloqueo (Fase I, o fase ya calculada
     * pero todavia no le toca). No dice si YA la firmo — eso lo decide quien la
     * llame consultando el repositorio (ver ConsultarContratosPendientesUseCase).
     */
    public static FasePrograma faseAFirmarEnDia(int diaProgramaActual) {
        FasePrograma actual = paraDiaPrograma(diaProgramaActual);
        return actual.firmaDesbloqueadaEnDia(diaProgramaActual) ? actual : null;
    }

    /** Inverso de {@link #numero()} — usado por ContratoFaseFinder (api) para no exponer este enum afuera. */
    public static FasePrograma porNumero(int numero) {
        for (FasePrograma fase : values()) {
            if (fase.numero == numero) {
                return fase;
            }
        }
        throw new IllegalArgumentException("Numero de fase invalido: " + numero);
    }
}
