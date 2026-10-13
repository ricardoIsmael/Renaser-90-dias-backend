package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.FuncionalidadEnConsola;
import com.renaser.os.plataforma.application.ports.in.FuncionalidadEnConsola.Actor;
import com.renaser.os.plataforma.application.ports.out.CargarEstadosPort;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.catalogo.ValorPorDefecto;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades.CircuitoAutomatico;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma la vista de la consola de cada funcionalidad: la definición del catálogo más su fila, leída de la BASE y no de
 * la copia en memoria —la consola muestra lo que está guardado aunque esta instancia todavía no haya recargado—, con
 * el nombre de quien la cambió y, en la IA, el estado de su circuito.
 */
@Component
class FuncionalidadesEnConsola {

    private final EstadoVigente estado;
    private final CargarEstadosPort cargarEstadosPort;
    private final UserSummaryFinder userSummaryFinder;

    FuncionalidadesEnConsola(EstadoVigente estado, CargarEstadosPort cargarEstadosPort,
                             UserSummaryFinder userSummaryFinder) {
        this.estado = estado;
        this.cargarEstadosPort = cargarEstadosPort;
        this.userSummaryFinder = userSummaryFinder;
    }

    List<FuncionalidadEnConsola> todas() {
        Map<String, EstadoDeFuncionalidad> filas = filasGuardadas();
        Map<UserId, UserSummary> autores = userSummaryFinder.findByIds(filas.values().stream()
                .map(EstadoDeFuncionalidad::actualizadoPor).distinct().toList());
        Map<String, CircuitoAutomatico> circuitos = circuitos();
        return estado.catalogo().todas().stream()
                .map(definicion -> vista(definicion, filas.get(definicion.clave()), autores, circuitos))
                .toList();
    }

    FuncionalidadEnConsola una(String clave) {
        return todas().stream().filter(f -> f.clave().equals(clave)).findFirst().orElseThrow();
    }

    Map<String, EstadoDeFuncionalidad> filasGuardadas() {
        return cargarEstadosPort.todos().stream()
                .collect(Collectors.toMap(EstadoDeFuncionalidad::clave, Function.identity()));
    }

    private Map<String, CircuitoAutomatico> circuitos() {
        return estado.automaticos().stream().flatMap(automatico -> automatico.circuitos().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(CircuitoAutomatico::parte, Function.identity(), (a, b) -> a));
    }

    private static FuncionalidadEnConsola vista(DefinicionDeFuncionalidad definicion, EstadoDeFuncionalidad fila,
                                                Map<UserId, UserSummary> autores,
                                                Map<String, CircuitoAutomatico> circuitos) {
        boolean prendida = fila != null ? fila.prendidaGlobal() : definicion.porDefecto() == ValorPorDefecto.PRENDIDA;
        Actor autor = fila == null ? null : new Actor(fila.actualizadoPor(),
                autores.containsKey(fila.actualizadoPor()) ? autores.get(fila.actualizadoPor()).fullName() : null);
        return new FuncionalidadEnConsola(definicion.clave(), definicion.madre(), definicion.area(),
                definicion.nombre(), definicion.descripcion(), definicion.rolesQueLaUsan(), definicion.critica(),
                definicion.porDefecto(), definicion.procesos(), prendida, porRol(fila),
                fila == null ? null : fila.aviso(), fila == null ? 0L : fila.version(), autor,
                fila == null ? null : fila.actualizadoEn(), circuitos.get(definicion.clave()));
    }

    /** Los siete roles, en el orden de {@code UserRole}: {@code false} solo los que tienen excepción propia. */
    private static Map<UserRole, Boolean> porRol(EstadoDeFuncionalidad fila) {
        Map<UserRole, Boolean> porRol = new EnumMap<>(UserRole.class);
        for (UserRole rol : UserRole.values()) {
            porRol.put(rol, fila == null || !fila.apagadaPara(rol));
        }
        return porRol;
    }
}
