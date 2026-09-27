package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable;
import com.renaser.os.chat.application.ports.in.bienvenida.BienvenidaEditable.TextoEditable;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaEnMemoria;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosOriginalesDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ver la bienvenida y cambiar sus mensajes desde la app (D-210): solo ADMIN y ALCHEMIST activos, con la
 * bitácora de quién y cuándo. Sin Spring ni base: la bitácora en memoria. El reloj a las 04:30 UTC, que en
 * Lima es el día anterior (.claude/rules/02): el instante se guarda tal cual, sin pasar por fechas.
 */
class TextosDeBienvenidaAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T04:30:00Z");
    private static final Map<PiezaDeBienvenida, String> ORIGINALES = Map.of(
            PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA, "{nombre}, esta tarjeta es para ti.",
            PiezaDeBienvenida.SOPORTE_FORMAL, "Hola, {nombre}. Tu ingreso está confirmado.",
            PiezaDeBienvenida.GRUPO, "¡Hola, {nombre}! Te acompaña {mentor}.");

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final AlmacenamientoPort almacenamiento = mock(AlmacenamientoPort.class);
    private final CambiosDeBienvenidaEnMemoria cambios = new CambiosDeBienvenidaEnMemoria();
    private final TextosOriginalesDeBienvenidaPort originales = ORIGINALES::get;
    private TextosDeBienvenidaAdminService servicio;

    @BeforeEach
    void armar() {
        servicio = new TextosDeBienvenidaAdminService(
                new BienvenidaParaAdministrar(originales, cambios, usuarios, almacenamiento, false), cambios,
                FixedClock.at(AHORA));
        lenient().when(almacenamiento.guardaObjetos()).thenReturn(true);
        lenient().when(usuarios.findByIds(any())).thenAnswer(inv -> {
            Collection<UserId> ids = inv.getArgument(0);
            return ids.stream().map(id -> usuarios.findById(id)).flatMap(Optional::stream)
                    .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        });
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "ALCHEMIST"})
    @DisplayName("ADMIN y ALCHEMIST ven los tres mensajes en orden, con su original y sin cambios")
    void adminYAlquimistaLaVen(UserRole rol) {
        BienvenidaEditable bienvenida = servicio.ver(cuenta("Kelin Rojas", rol, UserStatus.ACTIVE));

        assertThat(bienvenida.textos()).extracting(TextoEditable::pieza).containsExactly(
                PiezaDeBienvenida.SOPORTE_CON_LA_TARJETA, PiezaDeBienvenida.SOPORTE_FORMAL, PiezaDeBienvenida.GRUPO);
        assertThat(bienvenida.textos()).allSatisfy(t -> {
            assertThat(t.texto()).isEqualTo(ORIGINALES.get(t.pieza())).isEqualTo(t.original());
            assertThat(t.cambiado()).isFalse();
            assertThat(t.ultimoCambio()).isNull();
        });
        assertThat(bienvenida.textos().get(2).marcadores()).containsExactly("{nombre}", "{mentor}");
        assertThat(bienvenida.activa()).isFalse();
        assertThat(bienvenida.largoMaximo()).isEqualTo(1000);
        assertThat(bienvenida.portada().sePuedeCambiar()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TRAINEE", "MENTOR", "MENTOR_LEAD"})
    @DisplayName("autorización negativa: ningún otro rol la ve ni la cambia (403), y no queda nada escrito")
    void otroRolNo(UserRole rol) {
        UserId actor = cuenta("Otra Persona", rol, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.ver(actor)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.cambiar(actor, "SOPORTE_FORMAL", "Hola, {nombre}. Otro."))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.cambiar(actor, "NO_EXISTE", "x"))
                .as("403 antes que 400").isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.volverAlOriginal(actor, "GRUPO")).isInstanceOf(NotAuthorizedException.class);
        assertThat(cambios.filas()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "ALCHEMIST"})
    @DisplayName("autorización negativa: una cuenta SUSPENDIDA recibe 403 aunque tenga el rol")
    void suspendidaNo(UserRole rol) {
        UserId actor = cuenta("Kelin Rojas", rol, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.ver(actor)).isInstanceOf(NotAuthorizedException.class)
                .hasMessage("La cuenta esta suspendida");
        assertThatThrownBy(() -> servicio.cambiar(actor, "SOPORTE_FORMAL", "Hola, {nombre}. Otro."))
                .isInstanceOf(NotAuthorizedException.class);
        assertThat(cambios.filas()).isEmpty();
    }

    @Test
    @DisplayName("guardar deja el texto nuevo en la bitácora con quién y cuándo, y se ve como cambiado")
    void guardarQuedaEnLaBitacora() {
        UserId kelin = cuenta("Kelin Rojas", UserRole.ADMIN, UserStatus.ACTIVE);

        BienvenidaEditable bienvenida = servicio.cambiar(kelin, "SOPORTE_FORMAL", "  Hola, {nombre}. Bienvenida.  ");

        assertThat(cambios.filas()).containsExactly(new CambioDeBienvenida(PiezaDeBienvenida.SOPORTE_FORMAL,
                "Hola, {nombre}. Bienvenida.", kelin, AHORA));
        TextoEditable formal = bienvenida.textos().get(1);
        assertThat(formal.texto()).isEqualTo("Hola, {nombre}. Bienvenida.");
        assertThat(formal.original()).isEqualTo(ORIGINALES.get(PiezaDeBienvenida.SOPORTE_FORMAL));
        assertThat(formal.cambiado()).isTrue();
        assertThat(formal.ultimoCambio().por()).isEqualTo("Kelin Rojas");
        assertThat(formal.ultimoCambio().en()).isEqualTo(AHORA);
        assertThat(formal.ultimoCambio().volvioAlOriginal()).isFalse();
    }

    @Test
    @DisplayName("se rechaza un texto sin {nombre}, y no queda nada escrito")
    void sinNombreSeRechaza() {
        UserId kelin = cuenta("Kelin Rojas", UserRole.ALCHEMIST, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.cambiar(kelin, "SOPORTE_CON_LA_TARJETA", "Esta tarjeta es para ti."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{nombre}");
        assertThatThrownBy(() -> servicio.cambiar(kelin, "PORTADA", "{nombre}")).isInstanceOf(IllegalArgumentException.class);
        assertThat(cambios.filas()).isEmpty();
    }

    @Test
    @DisplayName("guardar el mismo texto que ya sale no escribe nada")
    void elMismoTextoNoEscribe() {
        UserId kelin = cuenta("Kelin Rojas", UserRole.ADMIN, UserStatus.ACTIVE);

        servicio.cambiar(kelin, "GRUPO", ORIGINALES.get(PiezaDeBienvenida.GRUPO));
        servicio.cambiar(kelin, "GRUPO", "¡Hola, {nombre}! Te va a acompañar {mentor}.");
        servicio.cambiar(kelin, "GRUPO", "¡Hola, {nombre}! Te va a acompañar {mentor}.");

        assertThat(cambios.filas()).hasSize(1);
    }

    @Test
    @DisplayName("volver al original deja la vuelta en la bitácora y sale el del repo; si ya salía, no escribe nada")
    void volverAlOriginal() {
        UserId kelin = cuenta("Kelin Rojas", UserRole.ADMIN, UserStatus.ACTIVE);
        servicio.volverAlOriginal(kelin, "GRUPO");
        assertThat(cambios.filas()).as("ya salía el original").isEmpty();

        servicio.cambiar(kelin, "GRUPO", "¡Hola, {nombre}! Te va a acompañar {mentor}.");
        BienvenidaEditable bienvenida = servicio.volverAlOriginal(kelin, "GRUPO");

        TextoEditable grupo = bienvenida.textos().get(2);
        assertThat(grupo.texto()).isEqualTo(ORIGINALES.get(PiezaDeBienvenida.GRUPO));
        assertThat(grupo.cambiado()).isFalse();
        assertThat(grupo.ultimoCambio().volvioAlOriginal()).isTrue();
        assertThat(cambios.filas()).hasSize(2);
        assertThat(cambios.filas().get(1).esVueltaAlOriginal()).isTrue();
    }

    @Test
    @DisplayName("si la cuenta de quien lo cambió ya no existe, el cambio se ve igual, sin nombre")
    void autorQueYaNoExiste() {
        UserId kelin = cuenta("Kelin Rojas", UserRole.ADMIN, UserStatus.ACTIVE);
        cambios.registrar(new CambioDeBienvenida(PiezaDeBienvenida.SOPORTE_FORMAL, "Hola, {nombre}.", null, AHORA));

        TextoEditable formal = servicio.ver(kelin).textos().get(1);

        assertThat(formal.cambiado()).isTrue();
        assertThat(formal.ultimoCambio().por()).isNull();
    }

    private UserId cuenta(String nombre, UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, nombre, null, rol, estado)));
        return id;
    }
}
