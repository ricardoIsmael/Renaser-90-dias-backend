package com.renaser.os.plataforma.domain.model.roles;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;

import java.util.List;
import java.util.Optional;

/**
 * Si una funcionalidad le corresponde a quien tiene un rol personalizado (spec 002, FR-030; contrato de la fase 2,
 * §4.4; D-272). Función pura, sin Spring: la usa la regla de disponibilidad {@code @Order(20)} en cada pedido.
 *
 * <ol>
 *   <li><b>Marcar una madre cubre a sus hijas</b>: si la clave pedida o alguna madre está marcada, le corresponde.</li>
 *   <li><b>Lo que el rol base no usa no lo decide el rol personalizado</b>: si ningún nivel de la cadena figura en
 *       {@code rolesQueLaUsan} para su rol base, la regla no hace nada (y deciden los guards, como para el rol base).
 *       Un rol personalizado solo puede marcar claves que su base usa, así que lo de afuera nunca se puede marcar;
 *       sin esta regla, un «Mentor junior» con TODO marcado perdería igual lo que el catálogo no lista para su base
 *       y la base usa por sus guards, y un alta pública con sesión ({@code registro}, que no usa nadie). Desvío
 *       anotado del contrato, que dice «si no, noDisponible». El caso que lo descubrió, {@code caja} para MENTOR, ya
 *       está corregido en el catálogo (E-620); la regla queda por {@code registro} y como resguardo ante otra omisión.</li>
 *   <li>Si no: <b>no disponible en la clave pedida</b> (§4.4). Con la madre sin marcar quedan fuera también las hijas.</li>
 * </ol>
 */
public final class DisponibilidadPorRolPersonalizado {

    private DisponibilidadPorRolPersonalizado() {
    }

    /**
     * @param cadena las claves desde la raíz hasta la pedida
     * @return la clave pedida si no le corresponde; vacío si le corresponde o si no es asunto del rol personalizado
     */
    public static Optional<String> noDisponibleEn(List<String> cadena, RolEnMemoria rol,
                                                  CatalogoDeFuncionalidades catalogo) {
        if (cadena.isEmpty() || cadena.stream().anyMatch(rol.funcionalidades()::contains)) {
            return Optional.empty();
        }
        boolean laBaseLaUsa = cadena.stream().map(catalogo::definicion).flatMap(Optional::stream)
                .map(DefinicionDeFuncionalidad::rolesQueLaUsan).anyMatch(roles -> roles.contains(rol.rolBase()));
        return laBaseLaUsa ? Optional.of(cadena.getLast()) : Optional.empty();
    }
}
