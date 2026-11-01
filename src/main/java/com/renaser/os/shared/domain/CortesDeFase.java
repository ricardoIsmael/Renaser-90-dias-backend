package com.renaser.os.shared.domain;

/**
 * Los cuatro cortes de fase del programa, <b>estirados en proporción a la duración</b> (spec 022, Q2-A: «las mismas 4 fases estiradas»).
 *
 * <p>Sobre 90 días las fases empiezan en los días 1, 8, 35 y 65 (1–7, 8–34, 35–64, 65–90). Con otra duración cada corte se escala:
 * {@code inicio(k, d) = (corte(k) − 1) · d / 90 + 1} con división entera. Con {@code d = 90} da exactamente los cortes de siempre; con
 * {@code d = 365}: 1, 29, 138 y 260. No se inventa contenido ni una tabla de cortes: es una sola cuenta, en un solo lugar.
 *
 * <p>Vive en el núcleo compartido porque las dos copias del enum de fase —{@code users.api.FasePrograma} y
 * {@code phasecontracts.domain.model.contrato.FasePrograma}, que no pueden importarse entre sí (D-21)— tienen que usar la MISMA regla.
 */
public final class CortesDeFase {

    /** Duración sobre la que están escritos los cortes originales. */
    public static final int DURACION_BASE = 90;
    /** Cantidad de fases del programa. */
    public static final int FASES = 4;

    private static final int[] CORTES_BASE = {1, 8, 35, 65};

    private CortesDeFase() {
    }

    /** Primer día (1-indexado) de la fase {@code numero} (1 a 4) en un programa de {@code duracion} días. */
    public static int inicioDeFase(int numero, int duracion) {
        exigirFase(numero);
        exigirDuracion(duracion);
        return (CORTES_BASE[numero - 1] - 1) * duracion / DURACION_BASE + 1;
    }

    /** Último día de la fase {@code numero}: el anterior al inicio de la siguiente, o el último día del programa en la cuarta. */
    public static int finDeFase(int numero, int duracion) {
        exigirFase(numero);
        exigirDuracion(duracion);
        return numero == FASES ? duracion : inicioDeFase(numero + 1, duracion) - 1;
    }

    /** La fase (1 a 4) en la que cae {@code dia}: la última cuyo inicio ya llegó; el día 0 y antes es la primera. */
    public static int numeroDeFase(int dia, int duracion) {
        exigirDuracion(duracion);
        for (int numero = FASES; numero > 1; numero--) {
            if (dia >= inicioDeFase(numero, duracion)) {
                return numero;
            }
        }
        return 1;
    }

    private static void exigirFase(int numero) {
        if (numero < 1 || numero > FASES) {
            throw new IllegalArgumentException("La fase va de 1 a " + FASES + ", llegó " + numero);
        }
    }

    private static void exigirDuracion(int duracion) {
        if (duracion < 1) {
            throw new IllegalArgumentException("La duración del programa es de al menos 1 día, llegó " + duracion);
        }
    }
}
