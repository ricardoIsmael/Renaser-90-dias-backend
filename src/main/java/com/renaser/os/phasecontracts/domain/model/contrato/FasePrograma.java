package com.renaser.os.phasecontracts.domain.model.contrato;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    /**
     * La fase cuyo pacto toca firmar ahora, contando los que quedaron atrás sin firmar, o null si no hay
     * ninguno (D-193).
     *
     * <p>Primero la fase EN CURSO, si ya se desbloqueó y no se firmó: es exactamente lo de
     * {@link #faseAFirmarEnDia}, así que la firma normal no cambia. Si esa no está pendiente, la fase
     * ANTERIOR más vieja cuyo pacto nunca se firmó. Es el caso de un ajuste de día que saltó por
     * encima del día de firma (del 10 al 40 se salta el 17): antes ese pacto ya no se podía firmar
     * nunca, porque solo se firmaba la fase en curso. Nunca devuelve una fase que todavía no llegó:
     * las posteriores a la en curso no están desbloqueadas.
     *
     * @param firmadas las fases que la persona ya firmó
     */
    public static FasePrograma faseAFirmar(int diaProgramaActual, Set<FasePrograma> firmadas) {
        List<FasePrograma> pendientes = pendientes(diaProgramaActual, firmadas);
        return pendientes.isEmpty() ? null : pendientes.getFirst();
    }

    /**
     * Todos los pactos pendientes, en el orden en que se ofrecen (D-193): el de la fase en curso primero,
     * si ya se desbloqueó y no se firmó; después los que quedaron atrás, del más viejo al más nuevo. En el
     * día 84 sin nada firmado: IV, II, III. Vacía si no hay ninguno. {@link #faseAFirmar} es el primero.
     *
     * <p>Existe desde D-216 (TRN-21 del e2e): con dos o más pendientes, un pedido de firma que no dice la
     * fase es ambiguo, y quien lo atiende necesita saber cuántos hay, no solo cuál va primero.
     *
     * @param firmadas las fases que la persona ya firmó
     */
    public static List<FasePrograma> pendientes(int diaProgramaActual, Set<FasePrograma> firmadas) {
        FasePrograma actual = paraDiaPrograma(diaProgramaActual);
        List<FasePrograma> pendientes = new ArrayList<>();
        if (actual.firmaDesbloqueadaEnDia(diaProgramaActual) && !firmadas.contains(actual)) {
            pendientes.add(actual);
        }
        for (FasePrograma anterior : values()) {
            boolean quedoAtras = anterior.numero < actual.numero;
            if (quedoAtras && anterior.firmaDesbloqueadaEnDia(diaProgramaActual) && !firmadas.contains(anterior)) {
                pendientes.add(anterior);
            }
        }
        return List.copyOf(pendientes);
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
