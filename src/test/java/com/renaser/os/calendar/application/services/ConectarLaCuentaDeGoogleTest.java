package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.meet.CuentaDeGoogleUseCase.ResultadoDeLaConexion;
import com.renaser.os.calendar.application.ports.in.meet.EstadoDeLaCuentaVista;
import com.renaser.os.calendar.application.ports.out.meet.AutorizacionGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.ConexionesEnCursoPort;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.calendar.domain.model.meet.EstadoDeLaCuenta;
import com.renaser.os.calendar.domain.model.meet.HuellaDeSecreto;
import com.renaser.os.calendar.domain.model.meet.RegistroDeAuditoriaGoogle;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.renaser.os.calendar.application.services.MundoDeMeet.ADMIN;
import static com.renaser.os.calendar.application.services.MundoDeMeet.CORREO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Conectar, desconectar y ver la cuenta de Google de Renaser (spec 040, FR-010, FR-011, FR-015, FR-016, Story 4). Con un Google de mentira para
 * el baile de OAuth: nada sale a la red.
 */
class ConectarLaCuentaDeGoogleTest {

    private static final String PERMISO_DE_CALENDARIO = CuentaDeGoogle.PERMISO_CALENDARIO_PROPIO;
    private static final String PERMISO_DE_LA_SALA = CuentaDeGoogle.PERMISO_AJUSTES_DE_LA_SALA;
    private static final String REFRESH_TOKEN = "1//refresh-token-secreto-de-prueba";

    /** Un Google que recuerda lo que le pidieron. */
    private static final class GoogleDeOAuth implements AutorizacionGooglePort {
        PermisoConcedido aConceder = new PermisoConcedido(REFRESH_TOKEN, Set.of(PERMISO_DE_CALENDARIO, PERMISO_DE_LA_SALA, "openid", "email"), CORREO);
        final List<String> revocados = new ArrayList<>();
        final List<String> calendariosCreados = new ArrayList<>();
        RuntimeException falla;
        String codeVerifierRecibido;

        Set<String> obligatorios = Set.of(PERMISO_DE_CALENDARIO, PERMISO_DE_LA_SALA);

        @Override
        public Set<String> permisosObligatorios() {
            return obligatorios;
        }

        @Override
        public Consentimiento pedirConsentimiento() {
            return new Consentimiento("https://accounts.google.com/o/oauth2/v2/auth?state=STATE-SECRETO", "STATE-SECRETO", "VERIFICADOR");
        }

        @Override
        public PermisoConcedido canjear(String codigo, String codeVerifier) {
            codeVerifierRecibido = codeVerifier;
            if (falla != null) {
                throw falla;
            }
            return aConceder;
        }

        @Override
        public void revocar(String refreshToken) {
            revocados.add(refreshToken);
        }

        @Override
        public String crearCalendarioPropio(String refreshToken, String nombre) {
            calendariosCreados.add(nombre);
            return "calendario-renaser@group.calendar.google.com";
        }
    }

    private static final class ConexionesEnMemoria implements ConexionesEnCursoPort {
        final Map<String, Object[]> guardadas = new HashMap<>();

        @Override
        public void guardar(String hashDelState, UserId actor, String codeVerifier, Instant venceEn) {
            guardadas.put(hashDelState, new Object[]{actor, codeVerifier, venceEn});
        }

        @Override
        public Optional<ConexionEnCurso> consumir(String hashDelState, Instant ahora) {
            Object[] fila = guardadas.remove(hashDelState);
            if (fila == null || !((Instant) fila[2]).isAfter(ahora)) {
                return Optional.empty();
            }
            return Optional.of(new ConexionEnCurso((UserId) fila[0], (String) fila[1]));
        }
    }

    private MundoDeMeet m;
    private GoogleDeOAuth oauth;
    private ConexionesEnMemoria conexiones;
    private CuentaDeGoogleService servicio;

    @BeforeEach
    void setUp() {
        m = new MundoDeMeet();
        oauth = new GoogleDeOAuth();
        conexiones = new ConexionesEnMemoria();
        servicio = new CuentaDeGoogleService(m.acceso, oauth, conexiones, m.cuenta, m.cuenta, m.salas, m.auditoriaDeGoogle, m.auditor,
                ids -> java.util.Map.of(), m.configuracion, m.reloj);
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: empezar la conexión devuelve la dirección de Google y guarda el state SOLO como hash, atado a quien la empezó, por 15 minutos")
    void empezarLaConexion() {
        String url = servicio.iniciarConexion(ADMIN);

        assertThat(url).startsWith("https://accounts.google.com/");
        assertThat(conexiones.guardadas).containsOnlyKeys(HuellaDeSecreto.de("STATE-SECRETO"));
        assertThat(conexiones.guardadas.keySet()).noneMatch(clave -> clave.contains("STATE-SECRETO"));
        Object[] fila = conexiones.guardadas.values().iterator().next();
        assertThat(fila[0]).isEqualTo(ADMIN);
        assertThat((Instant) fila[2]).isEqualTo(m.reloj.now().plus(Duration.ofMinutes(15)));
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: al volver de Google la cuenta queda conectada con su correo, sus permisos y su calendario propio «Renaser 90 días»")
    void volverDeGoogleConectaLaCuenta() {
        servicio.iniciarConexion(ADMIN);

        ResultadoDeLaConexion resultado = servicio.completarConexion("codigo-de-google", "STATE-SECRETO", null);

        assertThat(resultado.conectada()).isTrue();
        CuentaDeGoogle cuenta = m.cuenta.actual;
        assertThat(cuenta.correo()).isEqualTo(CORREO);
        assertThat(cuenta.estado()).isEqualTo(EstadoDeLaCuenta.ACTIVA);
        assertThat(cuenta.conectadaPor()).isEqualTo(ADMIN);
        assertThat(cuenta.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(oauth.codeVerifierRecibido).isEqualTo("VERIFICADOR");
        assertThat(oauth.calendariosCreados).containsExactly("Renaser 90 días");
        assertThat(cuenta.calendarioParaLasSalas()).isEqualTo("calendario-renaser@group.calendar.google.com");
        assertThat(m.auditoriaDeGoogle.filas).extracting(RegistroDeAuditoriaGoogle::accion).containsExactly(AccionDeAuditoriaGoogle.CONECTAR);
    }

    // spec 040 FR-010, FR-034
    @Test
    @DisplayName("FR-034: con el permiso reducido que no deja crear calendarios, las salas caen en el calendario principal")
    void sinPermisoDeCalendarioPropioSeUsaElPrincipal() {
        oauth.obligatorios = Set.of(CuentaDeGoogle.PERMISO_EVENTOS, PERMISO_DE_LA_SALA);
        oauth.aConceder = new AutorizacionGooglePort.PermisoConcedido(REFRESH_TOKEN,
                Set.of(CuentaDeGoogle.PERMISO_EVENTOS, PERMISO_DE_LA_SALA, "openid", "email"), CORREO);
        servicio.iniciarConexion(ADMIN);

        servicio.completarConexion("codigo", "STATE-SECRETO", null);

        assertThat(oauth.calendariosCreados).isEmpty();
        assertThat(m.cuenta.actual.calendarioParaLasSalas()).isEqualTo("primary");
    }

    // spec 040 FR-034
    @Test
    @DisplayName("FR-034: al reconectar la misma cuenta se reutiliza su calendario propio en vez de crear otro")
    void reconectarReutilizaElCalendario() {
        servicio.iniciarConexion(ADMIN);
        servicio.completarConexion("codigo", "STATE-SECRETO", null);
        servicio.iniciarConexion(ADMIN);

        servicio.completarConexion("codigo-2", "STATE-SECRETO", null);

        assertThat(oauth.calendariosCreados).hasSize(1);
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: el state es de un solo uso: la segunda vez ya no sirve")
    void elStateSeUsaUnaVez() {
        servicio.iniciarConexion(ADMIN);
        assertThat(servicio.completarConexion("codigo", "STATE-SECRETO", null).conectada()).isTrue();

        ResultadoDeLaConexion otraVez = servicio.completarConexion("codigo", "STATE-SECRETO", null);

        assertThat(otraVez.conectada()).isFalse();
        assertThat(otraVez.mensaje()).isEqualTo(CuentaDeGoogleService.CONEXION_VENCIDA);
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: el state vence a los 15 minutos")
    void elStateVence() {
        servicio.iniciarConexion(ADMIN);
        m.reloj.avanzar(Duration.ofMinutes(16));

        assertThat(servicio.completarConexion("codigo", "STATE-SECRETO", null).mensaje()).isEqualTo(CuentaDeGoogleService.CONEXION_VENCIDA);
        assertThat(m.cuenta.actual).isNull();
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: un state que nadie empezó (inventado) no conecta nada")
    void unStateInventadoNoConecta() {
        ResultadoDeLaConexion resultado = servicio.completarConexion("codigo", "state-inventado", null);

        assertThat(resultado.conectada()).isFalse();
        assertThat(m.cuenta.actual).isNull();
        assertThat(oauth.codeVerifierRecibido).as("ni siquiera se canjeó el código").isNull();
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: si la persona no acepta en Google, o la respuesta llega incompleta, se dice y no se conecta nada")
    void sinPermisoOIncompleto() {
        assertThat(servicio.completarConexion(null, "STATE-SECRETO", "access_denied").mensaje()).isEqualTo(CuentaDeGoogleService.SIN_PERMISO_DEL_USUARIO);
        assertThat(servicio.completarConexion(null, null, null).mensaje()).isEqualTo(CuentaDeGoogleService.RESPUESTA_INCOMPLETA);
        assertThat(servicio.completarConexion("codigo", " ", null).mensaje()).isEqualTo(CuentaDeGoogleService.RESPUESTA_INCOMPLETA);
        assertThat(m.cuenta.actual).isNull();
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: si Google no da todos los permisos, se revoca lo concedido y se dice cuál faltó")
    void faltaUnPermiso() {
        oauth.aConceder = new AutorizacionGooglePort.PermisoConcedido(REFRESH_TOKEN, Set.of(PERMISO_DE_CALENDARIO, "openid", "email"), CORREO);
        servicio.iniciarConexion(ADMIN);

        ResultadoDeLaConexion resultado = servicio.completarConexion("codigo", "STATE-SECRETO", null);

        assertThat(resultado.conectada()).isFalse();
        assertThat(resultado.mensaje()).contains("Falta: cambiar quién puede entrar a la sala");
        assertThat(oauth.revocados).containsExactly(REFRESH_TOKEN);
        assertThat(m.cuenta.actual).isNull();
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: si hay un correo esperado, otra cuenta no se conecta y se revoca")
    void otraCuentaNoSeConecta() {
        oauth.aConceder = new AutorizacionGooglePort.PermisoConcedido(REFRESH_TOKEN,
                Set.of(PERMISO_DE_CALENDARIO, PERMISO_DE_LA_SALA, "openid", "email"), "otra.persona@gmail.com");
        var conCorreoEsperado = new com.renaser.os.calendar.application.ports.out.meet.ConfiguracionDeGooglePort() {
            @Override
            public boolean estaConfigurada() {
                return true;
            }

            @Override
            public String correoEsperado() {
                return CORREO;
            }

            @Override
            public int ventanaEnDias() {
                return 60;
            }
        };
        CuentaDeGoogleService estricto = new CuentaDeGoogleService(m.acceso, oauth, conexiones, m.cuenta, m.cuenta, m.salas,
                m.auditoriaDeGoogle, m.auditor, ids -> java.util.Map.of(), conCorreoEsperado, m.reloj);
        estricto.iniciarConexion(ADMIN);

        ResultadoDeLaConexion resultado = estricto.completarConexion("codigo", "STATE-SECRETO", null);

        assertThat(resultado.mensaje()).isEqualTo(CuentaDeGoogleService.OTRA_CUENTA);
        assertThat(oauth.revocados).containsExactly(REFRESH_TOKEN);
        assertThat(m.cuenta.actual).isNull();
    }

    // spec 040 FR-010, FR-011
    @Test
    @DisplayName("FR-011: si Google rechaza el canje, la persona lee una frase simple: ni el código, ni el state, ni el motivo de Google")
    void unFalloDeGoogleNoFiltraNada() {
        oauth.falla = new GoogleRechazoException("Google no aceptó el pedido (código 400). codigo-de-google STATE-SECRETO", 400);
        servicio.iniciarConexion(ADMIN);

        ResultadoDeLaConexion resultado = servicio.completarConexion("codigo-de-google", "STATE-SECRETO", null);

        assertThat(resultado.conectada()).isFalse();
        assertThat(resultado.mensaje()).isEqualTo(CuentaDeGoogleService.NO_SE_PUDO).doesNotContain("codigo-de-google", "STATE-SECRETO");
    }

    // spec 040 FR-010, Story 4.2
    @Test
    @DisplayName("Story 4.2: desconectar revoca el permiso en Google, lo borra del servidor y deja la fila de auditoría; los enlaces ya creados siguen")
    void desconectar() {
        servicio.iniciarConexion(ADMIN);
        servicio.completarConexion("codigo", "STATE-SECRETO", null);
        m.google.eventos.put("id", "https://meet.google.com/abc-defg-hij");

        servicio.desconectar(ADMIN);

        assertThat(oauth.revocados).containsExactly(REFRESH_TOKEN);
        assertThat(m.cuenta.actual).isNull();
        assertThat(m.google.eventos).as("no se borra nada de lo ya creado").hasSize(1);
        assertThat(m.auditoriaDeGoogle.filas).extracting(RegistroDeAuditoriaGoogle::accion)
                .containsExactly(AccionDeAuditoriaGoogle.CONECTAR, AccionDeAuditoriaGoogle.DESCONECTAR);
    }

    // spec 040 Story 4.3
    @Test
    @DisplayName("Story 4.3: el estado dice si hay cuenta, de quién, desde cuándo y las últimas acciones; nunca el permiso permanente")
    void elEstado() {
        EstadoDeLaCuentaVista sin = servicio.estado(ADMIN);
        assertThat(sin.configurada()).isTrue();
        assertThat(sin.conectada()).isFalse();

        servicio.iniciarConexion(ADMIN);
        servicio.completarConexion("codigo", "STATE-SECRETO", null);
        EstadoDeLaCuentaVista con = servicio.estado(ADMIN);

        assertThat(con.conectada()).isTrue();
        assertThat(con.correo()).isEqualTo(CORREO);
        assertThat(con.estado()).isEqualTo(EstadoDeLaCuenta.ACTIVA);
        assertThat(con.acciones()).singleElement().satisfies(accion -> {
            assertThat(accion.accion()).isEqualTo(AccionDeAuditoriaGoogle.CONECTAR);
            assertThat(accion.exito()).isTrue();
        });
        assertThat(con.toString()).doesNotContain(REFRESH_TOKEN);
        assertThat(java.util.Arrays.stream(EstadoDeLaCuentaVista.class.getRecordComponents()).map(c -> c.getName()))
                .noneMatch(nombre -> nombre.toLowerCase().contains("token"));
    }

    // spec 040 FR-010
    @Test
    @DisplayName("FR-010: sin las credenciales de Google en el servidor no se puede empezar a conectar (409), y se dice por qué")
    void sinCredencialesNoSePuedeConectar() {
        var sinCredenciales = new com.renaser.os.calendar.application.ports.out.meet.ConfiguracionDeGooglePort() {
            @Override
            public boolean estaConfigurada() {
                return false;
            }

            @Override
            public String correoEsperado() {
                return "";
            }

            @Override
            public int ventanaEnDias() {
                return 60;
            }
        };
        CuentaDeGoogleService sinConfigurar = new CuentaDeGoogleService(m.acceso, oauth, conexiones, m.cuenta, m.cuenta, m.salas,
                m.auditoriaDeGoogle, m.auditor, ids -> java.util.Map.of(), sinCredenciales, m.reloj);

        assertThatThrownBy(() -> sinConfigurar.iniciarConexion(ADMIN)).isInstanceOf(IllegalStateException.class)
                .hasMessage(CuentaDeGoogleService.SIN_CREDENCIALES);
        assertThat(sinConfigurar.estado(ADMIN).configurada()).isFalse();
    }

    // spec 040 FR-015, SC-008
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = RolUsuario.class, names = {"TRAINEE", "MENTOR", "MENTOR_LEAD"})
    @DisplayName("SC-008: aprendiz, mentor y líder no conectan, desconectan ni ven la cuenta (403)")
    void soloAdministracionUsaLaCuenta(RolUsuario rol) {
        UserId persona = UserId.of(UUID.randomUUID());
        m.roles.put(persona, rol);

        assertThatThrownBy(() -> servicio.iniciarConexion(persona)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.desconectar(persona)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.estado(persona)).isInstanceOf(NotAuthorizedException.class);
    }
}
