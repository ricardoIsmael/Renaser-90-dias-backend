package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Las reglas de aceptar a alguien en una formación (spec 025, FR-002, FR-003, FR-013). Lógica pura: recibe el pedido, el rol final de la cuenta y el
 * catálogo, y devuelve lo que se decidió o dice por qué no.
 *
 * <ul>
 *   <li><b>Aprendiz</b>: una sola formación; si ofrece más de una duración, hay que elegirla.</li>
 *   <li><b>Mentor, guía de aprendiz y líder de mentores</b>: una o varias formaciones; la primera (por orden) es en la que hacen su programa propio,
 *       con la duración más corta que ofrece. No eligen duración.</li>
 *   <li><b>Administración y Alquimista</b>: no se les pide formación (ven las tres); su programa propio es el de la Escuela.</li>
 *   <li><b>Sin formación elegida</b> (una app anterior): Escuela y su duración por defecto, sin comprobar que esté activa — es lo que pasa hoy.</li>
 * </ul>
 */
public final class ReglasDeIngreso {

    private ReglasDeIngreso() {
    }

    /** Lo decidido: la foto para el historial y el programa propio con que se inscribe a la persona. */
    public record Resultado(EleccionDeIngreso eleccion, Formacion formacionDelPrograma, int duracionDelProgramaDias) {
    }

    /**
     * @param catalogo las tres formaciones tal como están configuradas
     * @throws IllegalArgumentException (400) si el pedido no cumple las reglas de su rol
     */
    public static Resultado decidir(UserRole rol, PedidoDeIngreso pedido, Map<Formacion, FormacionConfigurada> catalogo) {
        if (ReglaDeAlcance.veTodas(rol)) {
            return new Resultado(new EleccionDeIngreso(rol, Set.of(), null, pedido.conDiagnostico()),
                    Formacion.POR_DEFECTO, DuracionDelPrograma.POR_DEFECTO);
        }
        Set<Formacion> elegidas = pedido.sinFormacionElegida() ? EnumSet.of(Formacion.POR_DEFECTO) : EnumSet.copyOf(pedido.formaciones());
        if (rol == UserRole.TRAINEE && elegidas.size() > 1) {
            throw new IllegalArgumentException("Un aprendiz entra a una sola formación. Elige una.");
        }
        if (!pedido.sinFormacionElegida()) {
            elegidas.forEach(formacion -> exigirActiva(catalogo.get(formacion)));
        }
        Formacion principal = elegidas.iterator().next();
        FormacionConfigurada oferta = catalogo.get(principal);
        return rol == UserRole.TRAINEE
                ? delAprendiz(pedido, elegidas, principal, oferta)
                : new Resultado(new EleccionDeIngreso(rol, elegidas, null, pedido.conDiagnostico()), principal, oferta.duracionPorDefecto());
    }

    private static Resultado delAprendiz(PedidoDeIngreso pedido, Set<Formacion> elegidas, Formacion formacion, FormacionConfigurada oferta) {
        int duracion = duracionDelAprendiz(pedido, oferta);
        Integer elegida = pedido.sinFormacionElegida() ? null : duracion;
        return new Resultado(new EleccionDeIngreso(UserRole.TRAINEE, elegidas, elegida, pedido.conDiagnostico()), formacion, duracion);
    }

    private static int duracionDelAprendiz(PedidoDeIngreso pedido, FormacionConfigurada oferta) {
        if (pedido.duracionDias() != null) {
            if (!oferta.ofrece(pedido.duracionDias())) {
                throw new IllegalArgumentException("«" + oferta.nombre() + "» no ofrece esa duración.");
            }
            return pedido.duracionDias();
        }
        if (oferta.duracionesDias().size() > 1 && !pedido.sinFormacionElegida()) {
            throw new IllegalArgumentException("Elige la duración del programa en «" + oferta.nombre() + "».");
        }
        return oferta.duracionPorDefecto();
    }

    private static void exigirActiva(FormacionConfigurada oferta) {
        if (!oferta.activa()) {
            throw new IllegalArgumentException("«" + oferta.nombre() + "» está desactivada: no recibe personas nuevas.");
        }
    }
}
