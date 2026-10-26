package com.renaser.os.plataforma.domain.model.despliegue;

import java.util.Objects;

/**
 * Lo que Soporte TI pide al mover el escalón de un despliegue (contrato fase 2 §3.4,
 * {@code PUT /soporte-ti/funcionalidades/{clave}/despliegue}): el porcentaje nuevo, el motivo y, si la pantalla la
 * manda, la versión que estaba mirando.
 *
 * @param versionEsperada {@code null} si la pantalla no la mandó (es opcional en el contrato); 0 = «no había despliegue»
 */
public record CambioDeDespliegue(String clave, int porcentaje, String motivo, Long versionEsperada) {

    static final int LARGO_MAXIMO_DEL_MOTIVO = 500;

    public CambioDeDespliegue {
        Objects.requireNonNull(clave, "clave es obligatoria");
        if (porcentaje < 0 || porcentaje > DespliegueDeFuncionalidad.PARA_TODOS) {
            throw new IllegalArgumentException("El porcentaje va de 0 a 100.");
        }
        motivo = requerirMotivo(motivo);
        if (versionEsperada != null && versionEsperada < 0) {
            throw new IllegalArgumentException("La version no puede ser negativa");
        }
    }

    /** @param actual el despliegue en curso, o {@code null} si no había */
    public PasoDelDespliegue pasoDesde(DespliegueDeFuncionalidad actual) {
        if (actual == null) {
            return porcentaje == DespliegueDeFuncionalidad.PARA_TODOS ? PasoDelDespliegue.NINGUNO
                    : PasoDelDespliegue.EMPIEZA;
        }
        if (porcentaje == DespliegueDeFuncionalidad.PARA_TODOS) {
            return PasoDelDespliegue.TERMINA;
        }
        if (porcentaje == actual.porcentaje()) {
            return PasoDelDespliegue.NINGUNO;
        }
        return porcentaje > actual.porcentaje() ? PasoDelDespliegue.SUBE : PasoDelDespliegue.BAJA;
    }

    /**
     * Crítico (pide la contraseña otra vez, como en la fase 1), tal cual lo dice el contrato: empezar un despliegue o
     * bajar el porcentaje de una funcionalidad marcada crítica, y empezar uno al 0 % en cualquiera (la esconde para
     * todos salvo los probadores). Subir o terminar nunca lo es: solo suma personas.
     */
    public boolean esCritico(PasoDelDespliegue paso, boolean funcionalidadCritica) {
        return switch (paso) {
            case EMPIEZA -> funcionalidadCritica || porcentaje == 0;
            case BAJA -> funcionalidadCritica;
            case SUBE, TERMINA, NINGUNO -> false;
        };
    }

    /** El escalón nuevo en palabras, para el push a Soporte TI: «En despliegue: 5 % → 25 %». */
    public String resumen(PasoDelDespliegue paso, DespliegueDeFuncionalidad actual) {
        return switch (paso) {
            case EMPIEZA -> porcentaje == 0 ? "En despliegue solo para probadores (0 %)"
                    : "En despliegue al " + porcentaje + " %";
            case SUBE, BAJA -> "En despliegue: " + actual.porcentaje() + " % → " + porcentaje + " %";
            case TERMINA -> "Terminó el despliegue: llega a todos";
            case NINGUNO -> "Sin cambios";
        };
    }

    private static String requerirMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Escribe el motivo del cambio.");
        }
        String limpio = motivo.strip();
        if (limpio.length() > LARGO_MAXIMO_DEL_MOTIVO) {
            throw new IllegalArgumentException("El motivo no puede pasar de " + LARGO_MAXIMO_DEL_MOTIVO
                    + " caracteres.");
        }
        return limpio;
    }
}
