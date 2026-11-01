package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CortesDeFase;
import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ElegirFormacionActivaUseCase;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort.Pertenencia;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import com.renaser.os.users.application.ports.out.formacion.FormacionesDelPersonalPort;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * La formación de la sesión y la elección de la activa del personal (spec 022, FR-012, FR-013). Una lectura de la pertenencia y una del catálogo
 * (tres filas); no hay otra cosa que pedirle a la base.
 */
@Service
class SesionDeFormacionService implements ConsultarFormacionDeLaSesionUseCase, ElegirFormacionActivaUseCase {

    private static final String SIN_ASIGNAR = "No tienes asignada esa formación.";

    private final CargarPertenenciaPort pertenencias;
    private final CatalogoDeFormacionesPort catalogo;
    private final FormacionesDelPersonalPort personal;

    SesionDeFormacionService(CargarPertenenciaPort pertenencias, CatalogoDeFormacionesPort catalogo,
                             FormacionesDelPersonalPort personal) {
        this.pertenencias = pertenencias;
        this.catalogo = catalogo;
        this.personal = personal;
    }

    @Override
    public FormacionDeLaSesion consultar(UserId persona) {
        Pertenencia p = pertenencias.de(persona)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + persona));
        Map<Formacion, FormacionConfigurada> nombres = catalogo.todas().stream()
                .collect(Collectors.toMap(FormacionConfigurada::formacion, Function.identity()));
        int duracion = p.deLaParticipacion() == null ? DuracionDelPrograma.POR_DEFECTO : p.duracionDias();
        String delPrograma = p.deLaParticipacion() == null ? null : p.deLaParticipacion().codigo();
        if (ReglaDeAlcance.veTodas(p.rol())) {
            return new FormacionDeLaSesion(null, opciones(nombres, List.of(Formacion.values())), false, true, false, delPrograma,
                    duracion, fases(duracion));
        }
        if (ReglaDeAlcance.esPersonalConFormaciones(p.rol())) {
            return delPersonal(p, nombres, delPrograma, duracion);
        }
        Formacion suya = p.deLaParticipacion() == null ? Formacion.POR_DEFECTO : p.deLaParticipacion();
        return new FormacionDeLaSesion(opcion(nombres, suya), List.of(opcion(nombres, suya)), false, false, false, delPrograma,
                duracion, fases(duracion));
    }

    private FormacionDeLaSesion delPersonal(Pertenencia p, Map<Formacion, FormacionConfigurada> nombres, String delPrograma,
                                            int duracion) {
        List<Formacion> asignadas = p.asignadas().stream().map(Asignacion::formacion).sorted().toList();
        Optional<Formacion> activa = ReglaDeAlcance.formacionActiva(p.asignadas());
        return new FormacionDeLaSesion(activa.map(f -> opcion(nombres, f)).orElse(null), opciones(nombres, asignadas),
                asignadas.size() > 1, false, asignadas.isEmpty(), delPrograma, duracion, fases(duracion));
    }

    @Override
    @Transactional
    public FormacionDeLaSesion elegir(UserId persona, Formacion formacion) {
        Pertenencia p = pertenencias.de(persona).orElseThrow(() -> new NotAuthorizedException(SIN_ASIGNAR));
        boolean asignada = p.asignadas().stream().anyMatch(a -> a.formacion() == formacion);
        if (!ReglaDeAlcance.esPersonalConFormaciones(p.rol()) || !asignada) {
            throw new NotAuthorizedException(SIN_ASIGNAR);
        }
        personal.marcarActiva(persona, formacion);
        return consultar(persona);
    }

    private static List<OpcionDeFormacion> opciones(Map<Formacion, FormacionConfigurada> nombres, List<Formacion> formaciones) {
        return formaciones.stream().map(f -> opcion(nombres, f)).toList();
    }

    private static OpcionDeFormacion opcion(Map<Formacion, FormacionConfigurada> nombres, Formacion formacion) {
        FormacionConfigurada configurada = nombres.get(formacion);
        return new OpcionDeFormacion(formacion.codigo(), configurada == null ? formacion.codigo() : configurada.nombre());
    }

    /** Las cuatro fases de un programa de {@code duracion} días: las mismas de siempre, estiradas (Q2-A). */
    static List<FaseDelPrograma> fases(int duracion) {
        return IntStream.rangeClosed(1, CortesDeFase.FASES)
                .mapToObj(n -> new FaseDelPrograma(n, CortesDeFase.inicioDeFase(n, duracion), CortesDeFase.finDeFase(n, duracion)))
                .toList();
    }
}
