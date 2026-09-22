package com.renaser.os.points.domain.model.ranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Formula del ranking general de la plataforma: <b>75% habitos + 25% cursos</b>.
 *
 * <blockquote><b>Corregido el 2026-09-22 (decision del dueno).</b> Aca decia <i>"50% habitos + 35%
 * rocas + 15% cursos"</i>, porte literal de {@code general_ranking_scores()} del backend viejo. Las
 * rocas <b>salen del ranking general</b>: pasaron a llamarse OBJETIVOS y su porcentaje ya es la
 * COHERENCIA, que se muestra aparte en Hoy ({@code HomeAgregadoService.coherenciaDe}, D-128) y es
 * ademas lo que ordena el ranking de celula. Tenerlas contando en los dos lados hacia que un
 * aprendiz que todavia no planifico una accion perdiera puesto en el general por algo que el
 * general no deberia medir.</blockquote>
 *
 * <p><b>Los dos pesos suman 1 exacto</b> (0.75 y 0.25, decision del dueno el 2026-09-22): con los
 * dos modulos presentes la division de abajo es por 1 y el puntaje es la ponderacion directa. El
 * 35% que dejaron las rocas se repartio entre los dos que quedaron — habitos subio de 0.50 a 0.75
 * y cursos de 0.15 a 0.25.
 *
 * <p><b>Por que vive en el dominio y no en SQL (D-43):</b> el motivo por el que el equipo viejo
 * bajo esto a un procedimiento almacenado fue de rendimiento — el calculo hacia una consulta POR
 * aprendiz y con ~30 cuentas activas agotaba las conexiones. Ese problema era el N+1, y se
 * resuelve con consultas en lote. La formula en si es regla de negocio y aca se puede probar sin
 * levantar Postgres.
 *
 * <p><b>Redondeos:</b> cada componente ya llega con un decimal (lo garantizan los finders); la
 * ponderacion se redondea a un decimal al final.
 *
 * <p><b>El modulo sin dato NO puntua: sale del promedio y su peso se reparte entre los que si
 * tienen dato</b> (2026-09-15, D-131).
 *
 * <blockquote><b>Corregido el 2026-09-15.</b> Aca decia: <i>"Sin dato = 100, no 0: el SQL usa
 * {@code COALESCE(hp.pct, 100)} para habitos y rocas, y {@code CASE WHEN total = 0 THEN 100} para
 * cursos. Un aprendiz que recien empieza no tiene nada calificable todavia y no se lo castiga por
 * eso."</i> El porte era fiel al backend viejo y el razonamiento —no castigar a quien recien
 * empieza— es correcto. Lo que no cierra es la conclusion: rellenar con 100 no es dejar de
 * castigarlo, es <b>premiarlo</b>, y en una tabla ORDENADA eso lo pone por delante de quien si
 * viene cumpliendo. Un aprendiz sin un solo dato encabezaba el ranking general con 100.</blockquote>
 *
 * <p>Renormalizar es la unica salida que no inventa un valor: no lo castiga con un 0 por algo que
 * todavia no existe, ni lo premia con un 100 que no gano.
 */
public final class PuntajeGeneral {

    /**
     * Los dos unicos pesos del ranking general. <b>Cambiar cualquiera de estos dos numeros cambia
     * el orden de la tabla para todo el padron</b>, asi que no se tocan sin decision escrita del
     * dueno. Suman 1 exacto.
     */
    private static final BigDecimal PESO_HABITOS = new BigDecimal("0.75");
    private static final BigDecimal PESO_CURSOS = new BigDecimal("0.25");

    private static final int DECIMALES = 1;

    private PuntajeGeneral() {
    }

    /**
     * @param porcentajeHabitos cualquiera de los dos puede venir {@code null}: es "sin dato", que
     *                          no es cero ni cien — simplemente no entra en la cuenta
     * @param porcentajeCursos  idem
     * @return el puntaje, o {@link Optional#empty()} si los DOS vinieron sin dato: ahi no hay nada
     *         que promediar, y quien llama decide que hacer con eso (el ranking lo manda al fondo
     *         de la tabla, no al frente)
     */
    public static Optional<BigDecimal> calcular(BigDecimal porcentajeHabitos, BigDecimal porcentajeCursos) {
        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal pesos = BigDecimal.ZERO;
        if (porcentajeHabitos != null) {
            suma = suma.add(porcentajeHabitos.multiply(PESO_HABITOS));
            pesos = pesos.add(PESO_HABITOS);
        }
        if (porcentajeCursos != null) {
            suma = suma.add(porcentajeCursos.multiply(PESO_CURSOS));
            pesos = pesos.add(PESO_CURSOS);
        }
        if (pesos.signum() == 0) {
            return Optional.empty();
        }
        // Con UN solo modulo con dato, `suma / pesos` devuelve ese mismo porcentaje: quien solo
        // tiene habitos compite con su porcentaje de habitos, sin relleno inventado.
        return Optional.of(suma.divide(pesos, DECIMALES, RoundingMode.HALF_UP));
    }
}
