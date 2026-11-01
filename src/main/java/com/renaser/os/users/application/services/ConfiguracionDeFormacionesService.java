package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarFormacionUseCase;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Edita una formación (spec 022, FR-003, FR-004). Cambiar las duraciones NO mueve a nadie que ya empezó: la duración de cada persona vive en su
 * participación ({@code participantes_programa.duracion_dias}), no en el catálogo.
 */
@Service
class ConfiguracionDeFormacionesService implements ConfigurarFormacionUseCase {

    static final String OTRA_PERSONA_LA_CAMBIO = "Otra persona cambió esta formación mientras la editabas. Ábrela de nuevo para ver lo último.";
    private static final int NOMBRE_MINIMO = 3;
    private static final int NOMBRE_MAXIMO = 80;
    private static final int DESCRIPCION_MAXIMA = 600;
    private static final int DURACIONES_MAXIMAS = 4;

    private final RequireAdminGuard requireAdminGuard;
    private final CatalogoDeFormacionesPort catalogo;

    ConfiguracionDeFormacionesService(RequireAdminGuard requireAdminGuard, CatalogoDeFormacionesPort catalogo) {
        this.requireAdminGuard = requireAdminGuard;
        this.catalogo = catalogo;
    }

    @Override
    @Transactional
    public FormacionConfigurada configurar(ConfigurarFormacionCommand command) {
        requireAdminGuard.requireAdminActivo(command.actor());
        FormacionConfigurada nueva = new FormacionConfigurada(command.formacion(), nombreValido(command.nombre()),
                descripcionValida(command.descripcion()), command.activa(), duracionesValidas(command.duracionesDias()),
                command.version() + 1);
        if (!catalogo.guardar(nueva, command.version(), command.actor())) {
            throw new IllegalStateException(OTRA_PERSONA_LA_CAMBIO);
        }
        return catalogo.de(command.formacion());
    }

    private static String nombreValido(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.length() < NOMBRE_MINIMO || limpio.length() > NOMBRE_MAXIMO) {
            throw new IllegalArgumentException("El nombre va de " + NOMBRE_MINIMO + " a " + NOMBRE_MAXIMO + " letras.");
        }
        return limpio;
    }

    private static String descripcionValida(String descripcion) {
        String limpia = descripcion == null ? "" : descripcion.trim();
        if (limpia.length() > DESCRIPCION_MAXIMA) {
            throw new IllegalArgumentException("La descripción puede tener hasta " + DESCRIPCION_MAXIMA + " letras.");
        }
        return limpia;
    }

    private static List<Integer> duracionesValidas(List<Integer> duraciones) {
        if (duraciones == null || duraciones.isEmpty() || duraciones.size() > DURACIONES_MAXIMAS) {
            throw new IllegalArgumentException("Pon de 1 a " + DURACIONES_MAXIMAS + " duraciones.");
        }
        List<Integer> ordenadas = duraciones.stream().distinct().sorted().toList();
        if (ordenadas.size() != duraciones.size()) {
            throw new IllegalArgumentException("Las duraciones no pueden repetirse.");
        }
        ordenadas.forEach(dias -> DuracionDelPrograma.validar(dias));
        return ordenadas;
    }
}
