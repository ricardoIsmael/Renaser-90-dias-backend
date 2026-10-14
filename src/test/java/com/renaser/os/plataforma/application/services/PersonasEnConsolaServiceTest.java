package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.plataforma.application.ports.in.BuscarPersonasUseCase;
import com.renaser.os.plataforma.application.ports.in.BuscarPersonasUseCase.PersonaEncontrada;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BusquedaDeUsuariosFinder;
import com.renaser.os.users.api.CorreosDeUsuariosFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Buscar a quién sumar como probadora (contrato fase 2 §3.4, {@code GET /soporte-ti/personas?q=}). */
class PersonasEnConsolaServiceTest {

    private static final String SESION = "sesion-1";

    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final Dobles.Tokens tokens = new Dobles.Tokens();
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final BusquedaDeUsuariosFinder busqueda = mock(BusquedaDeUsuariosFinder.class);
    private final CorreosDeUsuariosFinder correos = mock(CorreosDeUsuariosFinder.class);
    private PersonasEnConsolaService service;
    private String consola;

    @BeforeEach
    void armar() {
        when(usuarios.findById(soporte)).thenReturn(Optional.of(
                new UserSummary(soporte, "Dueño", null, UserRole.IT_SUPPORT, UserStatus.ACTIVE)));
        service = new PersonasEnConsolaService(new GuardiaDeSoporteTi(usuarios, tokens, Duration.ofMinutes(15)),
                busqueda, usuarios, correos);
        consola = tokens.emitirDeConsola(soporte, SESION, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("solo las activas, por nombre, con correo y rol, hasta 20")
    void activasPorNombreHastaVeinte() {
        Map<UserId, UserSummary> encontradas = new HashMap<>();
        IntStream.range(0, 25).forEach(i -> {
            UserId id = UserId.of(UUID.randomUUID());
            encontradas.put(id, new UserSummary(id, "María %02d".formatted(24 - i), null, UserRole.TRAINEE,
                    UserStatus.ACTIVE));
        });
        UserId suspendida = UserId.of(UUID.randomUUID());
        encontradas.put(suspendida, new UserSummary(suspendida, "María 00 suspendida", null, UserRole.MENTOR,
                UserStatus.SUSPENDED));
        when(busqueda.coincidenCon("maria")).thenReturn(Set.copyOf(encontradas.keySet()));
        when(usuarios.findByIds(anyCollection())).thenReturn(encontradas);
        when(correos.correosDe(anyCollection())).thenReturn(encontradas.keySet().stream()
                .collect(Collectors.toMap(id -> id, id -> id + "@renaser.test")));

        List<PersonaEncontrada> personas = service.buscar(acceso(), "maria");

        assertThat(personas).hasSize(BuscarPersonasUseCase.MAXIMO);
        assertThat(personas.getFirst().nombre()).isEqualTo("María 00");
        assertThat(personas.getFirst().email()).isEqualTo(personas.getFirst().id() + "@renaser.test");
        assertThat(personas.getFirst().rol()).isEqualTo(UserRole.TRAINEE);
        assertThat(personas).extracting(PersonaEncontrada::id).doesNotContain(suspendida);
    }

    @Test
    @DisplayName("«ver como» (D-270): una cuenta de prueba nunca sale en la búsqueda, aunque la búsqueda la devolviera")
    void sinCuentasDePrueba() {
        UserId real = UserId.of(UUID.randomUUID());
        UserId dePrueba = UserId.of(UUID.randomUUID());
        Map<UserId, UserSummary> encontradas = Map.of(
                real, new UserSummary(real, "Mentora real", null, UserRole.MENTOR, UserStatus.ACTIVE),
                dePrueba, new UserSummary(dePrueba, "Prueba · Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE, null,
                        true));
        when(busqueda.coincidenCon("mentor")).thenReturn(encontradas.keySet());
        when(usuarios.findByIds(anyCollection())).thenReturn(encontradas);
        when(correos.correosDe(anyCollection())).thenReturn(Map.of(real, "real@renaser.test"));

        assertThat(service.buscar(acceso(), "mentor")).extracting(PersonaEncontrada::id).containsExactly(real);
    }

    @Test
    @DisplayName("sin texto no busca nada; un ADMIN no busca (403)")
    void sinTextoYCerrojo() {
        when(busqueda.coincidenCon(" ")).thenReturn(Set.of());

        assertThat(service.buscar(acceso(), " ")).isEmpty();
        UserId admin = UserId.of(UUID.randomUUID());
        when(usuarios.findById(admin)).thenReturn(Optional.of(
                new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        assertThatThrownBy(() -> service.buscar(new AccesoDeConsola(admin, consola, SESION), "maria"))
                .isInstanceOf(NotAuthorizedException.class);
    }

    private AccesoDeConsola acceso() {
        return new AccesoDeConsola(soporte, consola, SESION);
    }
}
