package com.renaser.os.plataforma.domain.model.catalogo;

import com.renaser.os.plataforma.domain.model.estado.CambioDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.domain.ClavesDeFuncionalidad;
import com.renaser.os.users.api.UserRole;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * El «un solo interruptor» de la spec 034 sobre tres roles: si el mentor, la guía y el líder de mentores ven el Código
 * Renaser en las fichas de los aprendices. Vive como la funcionalidad {@code radar.fichas} del catálogo; esta clase solo
 * sabe leer su estado como un sí o un no y armar el cambio que lo lleva de uno al otro. Pura: sin Spring ni base.
 *
 * <p><b>Visible</b> = la funcionalidad está prendida y ninguno de los tres roles está apagado. <b>Oculto</b> = los tres
 * apagados (o sin fila, que es como nace). Cualquier otra cosa —Soporte TI dejó uno distinto desde su consola— es
 * <b>mixto</b>: se lee como «no lo ven todos» ({@code visible = false}) y el detalle va en {@code porRol}.
 */
public final class CodigoRenaserEnFichas {

    public static final String CLAVE = ClavesDeFuncionalidad.RADAR_EN_FICHAS;

    /** Los roles que gobierna el interruptor. Administración y Alquimista no figuran: lo ven siempre. */
    public static final Set<UserRole> ROLES = Set.copyOf(EnumSet.of(UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.MENTOR_LEAD));

    /** El motivo que queda en la auditoría de un cambio hecho desde Administración, que no pide uno escrito (decisión 034-5). */
    public static final String MOTIVO_DEL_CAMBIO = "Cambio desde Administración (Código Renaser en las fichas)";

    private CodigoRenaserEnFichas() {
    }

    /**
     * @param visible si los tres roles lo ven
     * @param mixto   si no coinciden (algunos lo ven y otros no)
     * @param porRol  si cada uno de los tres lo ve
     */
    public record Estado(boolean visible, boolean mixto, Map<UserRole, Boolean> porRol) {

        public Estado {
            porRol = Map.copyOf(porRol);
        }
    }

    /** El estado guardado (vacío = nunca se cambió: apagado, como nace). */
    public static Estado estadoDe(Optional<EstadoDeFuncionalidad> fila) {
        Map<UserRole, Boolean> porRol = new EnumMap<>(UserRole.class);
        for (UserRole rol : ROLES) {
            porRol.put(rol, fila.map(f -> f.prendidaGlobal() && !f.apagadaPara(rol)).orElse(false));
        }
        long quienesLoVen = porRol.values().stream().filter(Boolean::booleanValue).count();
        return new Estado(quienesLoVen == ROLES.size(), quienesLoVen > 0 && quienesLoVen < ROLES.size(), porRol);
    }

    /** ¿Ya rige lo que se pide? Pedir el valor vigente no escribe nada. */
    public static boolean yaRige(boolean visible, Optional<EstadoDeFuncionalidad> fila) {
        Estado actual = estadoDe(fila);
        return visible ? actual.visible() : !actual.visible() && !actual.mixto();
    }

    /**
     * El cambio que deja a los tres roles como se pidió, sin tocar lo demás: la funcionalidad queda prendida para todos
     * (Administración y Alquimista la ven siempre), los otros roles que Soporte TI hubiera apagado siguen como estaban y
     * el aviso se conserva.
     *
     * @param anterior la fila vigente, o vacío si nunca se cambió
     */
    public static CambioDeFuncionalidad cambioHacia(boolean visible, Optional<EstadoDeFuncionalidad> anterior) {
        Set<UserRole> apagadas = EnumSet.noneOf(UserRole.class);
        anterior.ifPresent(fila -> apagadas.addAll(fila.apagadaPara()));
        if (visible) {
            apagadas.removeAll(ROLES);
        } else {
            apagadas.addAll(ROLES);
        }
        return new CambioDeFuncionalidad(CLAVE, true, apagadas, anterior.map(EstadoDeFuncionalidad::aviso).orElse(null),
                MOTIVO_DEL_CAMBIO, anterior.map(EstadoDeFuncionalidad::version).orElse(0L));
    }
}
