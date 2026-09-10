package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Parámetros de operación de una cohorte. Los valores por defecto son las propuestas
 * P-01/P-02/P-06 adoptadas <b>como configuración</b>: cambiarlas es un UPDATE, no un
 * despliegue, y por eso ninguna vive como constante en una regla.
 *
 * <p>{@link #porDefecto(CohorteId)} existe porque una cohorte creada después de V45 no tiene
 * fila: el backfill solo alcanzó a las de entonces. Este proyecto decidió no usar triggers
 * ("la garantía primaria es la transacción del caso de uso", V1:1519), así que la ausencia
 * se resuelve acá y nunca revienta la lectura.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "cohorteId")
public final class PoliticaMentoria {

    public static final int CAPACIDAD_POR_DEFECTO = 10;
    public static final int DIA_TRASLADO_POR_DEFECTO = 4;
    public static final int DIAS_SIN_ACTIVIDAD_POR_DEFECTO = 3;
    public static final String ZONA_POR_DEFECTO = "America/Lima";

    private final CohorteId cohorteId;
    private int capacidadCelula;
    private CadenciaRotacion cadenciaRotacion;
    private String zonaHoraria;
    private int diaTraslado;
    private int diasSinActividadAlerta;
    private CelulaId celulaRecepcionId;
    private int version;
    /** {@code false} cuando la fila no existe todavía y estos son los valores implícitos. */
    private final boolean persistida;

    public static PoliticaMentoria porDefecto(CohorteId cohorteId) {
        return new PoliticaMentoria(cohorteId, CAPACIDAD_POR_DEFECTO, CadenciaRotacion.MENSUAL, ZONA_POR_DEFECTO,
                DIA_TRASLADO_POR_DEFECTO, DIAS_SIN_ACTIVIDAD_POR_DEFECTO, null, 1, false);
    }

    /** Solo para el adaptador de persistencia. */
    public static PoliticaMentoria rehydrate(CohorteId cohorteId, int capacidadCelula,
                                              CadenciaRotacion cadenciaRotacion, String zonaHoraria, int diaTraslado,
                                              int diasSinActividadAlerta, CelulaId celulaRecepcionId, int version) {
        PoliticaMentoria politica = new PoliticaMentoria(cohorteId, capacidadCelula, cadenciaRotacion, zonaHoraria,
                diaTraslado, diasSinActividadAlerta, celulaRecepcionId, version, true);
        politica.validar();
        return politica;
    }

    /**
     * @param versionEsperada versión que el administrador tenía en pantalla. Si no coincide,
     *                        otro la editó mientras tanto y su cambio no se pisa en silencio.
     */
    public void reconfigurar(int capacidadCelula, CadenciaRotacion cadenciaRotacion, String zonaHoraria,
                              int diaTraslado, int diasSinActividadAlerta, int versionEsperada) {
        if (persistida && versionEsperada != this.version) {
            throw new PoliticaDesactualizadaException(this.version, versionEsperada);
        }
        this.capacidadCelula = capacidadCelula;
        this.cadenciaRotacion = Objects.requireNonNull(cadenciaRotacion, "cadenciaRotacion es obligatoria");
        this.zonaHoraria = zonaHoraria;
        this.diaTraslado = diaTraslado;
        this.diasSinActividadAlerta = diasSinActividadAlerta;
        validar();
        this.version = this.version + 1;
    }

    public void designarRecepcion(CelulaId celulaRecepcionId) {
        this.celulaRecepcionId = celulaRecepcionId;
    }

    public ZoneId zona() {
        return ZoneId.of(zonaHoraria);
    }

    /** Cupo de un grupo regular, salvo que la célula traiga su propio override. */
    public CupoCelula cupoRegular(Integer overrideDeCelula) {
        return CupoCelula.regular(overrideDeCelula != null ? overrideDeCelula : capacidadCelula);
    }

    /** Un aprendiz sale de recepción cuando su día de programa alcanza {@code diaTraslado}. */
    public boolean correspondeTraslado(int diaDePrograma) {
        return diaDePrograma >= diaTraslado;
    }

    /** Día local de la cohorte para un instante dado. La fecha del servidor no es la de nadie. */
    public java.time.LocalDate fechaLocalDe(Instant instante) {
        return instante.atZone(zona()).toLocalDate();
    }

    private void validar() {
        // Delegar el rango de capacidad en CupoCelula evita dos definiciones del mismo limite.
        CupoCelula.regular(capacidadCelula);
        if (diaTraslado < 2 || diaTraslado > 15) {
            throw new IllegalArgumentException("diaTraslado va de 2 a 15, llego " + diaTraslado);
        }
        if (diasSinActividadAlerta < 1 || diasSinActividadAlerta > 30) {
            throw new IllegalArgumentException(
                    "diasSinActividadAlerta va de 1 a 30, llego " + diasSinActividadAlerta);
        }
        try {
            ZoneId.of(zonaHoraria);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("zonaHoraria no es una zona valida: " + zonaHoraria);
        }
    }
}
