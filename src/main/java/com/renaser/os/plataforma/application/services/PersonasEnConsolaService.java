package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.BuscarPersonasUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BusquedaDeUsuariosFinder;
import com.renaser.os.users.api.CorreosDeUsuariosFinder;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Buscar a quién sumar como probadora (contrato fase 2 §3.4, {@code GET /soporte-ti/personas?q=}). La búsqueda sin
 * tildes ni mayúsculas es la de {@code users} (D-249); acá se dejan las cuentas activas, ordenadas por nombre, hasta 20.
 *
 * <p>Las cuentas de prueba de «ver como» (B3, {@code usuarios.es_cuenta_de_prueba}, V107) todavía no existen: cuando
 * entren, se excluyen acá.
 */
@Service
class PersonasEnConsolaService implements BuscarPersonasUseCase {

    private final GuardiaDeSoporteTi guardia;
    private final BusquedaDeUsuariosFinder busqueda;
    private final UserSummaryFinder userSummaryFinder;
    private final CorreosDeUsuariosFinder correosFinder;

    PersonasEnConsolaService(GuardiaDeSoporteTi guardia, BusquedaDeUsuariosFinder busqueda,
                             UserSummaryFinder userSummaryFinder, CorreosDeUsuariosFinder correosFinder) {
        this.guardia = guardia;
        this.busqueda = busqueda;
        this.userSummaryFinder = userSummaryFinder;
        this.correosFinder = correosFinder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonaEncontrada> buscar(AccesoDeConsola acceso, String texto) {
        guardia.exigirConsolaAbierta(acceso);
        Set<UserId> coinciden = busqueda.coincidenCon(texto);
        if (coinciden.isEmpty()) {
            return List.of();
        }
        List<UserSummary> activas = userSummaryFinder.findByIds(coinciden).values().stream()
                .filter(persona -> persona.status() == UserStatus.ACTIVE)
                .sorted(Comparator.comparing(UserSummary::fullName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(persona -> persona.id().value()))
                .limit(MAXIMO)
                .toList();
        Map<UserId, String> correos = correosFinder.correosDe(activas.stream().map(UserSummary::id).toList());
        return activas.stream().map(persona -> new PersonaEncontrada(persona.id(), persona.fullName(),
                correos.get(persona.id()), persona.role())).toList();
    }
}
