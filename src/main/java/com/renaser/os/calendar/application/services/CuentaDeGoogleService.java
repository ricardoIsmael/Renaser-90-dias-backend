package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.meet.CuentaDeGoogleUseCase;
import com.renaser.os.calendar.application.ports.in.meet.EstadoDeLaCuentaVista;
import com.renaser.os.calendar.application.ports.in.meet.EstadoDeLaCuentaVista.AccionVista;
import com.renaser.os.calendar.application.ports.out.meet.AuditoriaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.AutorizacionGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.AutorizacionGooglePort.Consentimiento;
import com.renaser.os.calendar.application.ports.out.meet.AutorizacionGooglePort.PermisoConcedido;
import com.renaser.os.calendar.application.ports.out.meet.ConexionesEnCursoPort;
import com.renaser.os.calendar.application.ports.out.meet.ConexionesEnCursoPort.ConexionEnCurso;
import com.renaser.os.calendar.application.ports.out.meet.ConfiguracionDeGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.SaveCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.SaveSalaMeetPort;
import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort;
import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort.Persona;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.calendar.domain.model.meet.HuellaDeSecreto;
import com.renaser.os.calendar.domain.model.meet.RegistroDeAuditoriaGoogle;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Conectar, desconectar y ver la cuenta de Google que organiza las salas de Meet (spec 040, FR-010, FR-015). Una sola cuenta para toda la
 * plataforma. El permiso permanente lo guarda el puerto cifrado; este servicio nunca lo escribe en un registro, un mensaje ni una respuesta.
 */
@Service
class CuentaDeGoogleService implements CuentaDeGoogleUseCase {

    private static final Logger log = LoggerFactory.getLogger(CuentaDeGoogleService.class);

    static final Duration VIGENCIA_DE_LA_CONEXION = Duration.ofMinutes(15);
    static final String NOMBRE_DEL_CALENDARIO = "Renaser 90 días";
    static final int ACCIONES_A_LA_VISTA = 20;

    static final String SIN_CREDENCIALES = "El servidor todavía no tiene las credenciales de Google. Pídele a quien lo administra que las cargue.";
    static final String RESPUESTA_INCOMPLETA = "La respuesta de Google llegó incompleta. Empieza otra vez desde Renaser.";
    static final String CONEXION_VENCIDA = "Esta conexión venció o ya se usó. Empieza otra vez desde Renaser.";
    static final String SIN_PERMISO_DEL_USUARIO = "Google no dio el permiso. Si quieres conectarla, empieza otra vez desde Renaser y acepta.";
    static final String NO_SE_PUDO = "No pudimos terminar la conexión con Google. Inténtalo de nuevo en unos minutos.";
    static final String CONECTADA = "Listo: la cuenta de Google quedó conectada. Ya puedes volver a Renaser.";
    static final String OTRA_CUENTA = "Esa no es la cuenta de Google de Renaser. Entra con la cuenta correcta.";

    private final AccesoEventoService accesoEventoService;
    private final AutorizacionGooglePort autorizacion;
    private final ConexionesEnCursoPort conexiones;
    private final LoadCuentaGooglePort loadCuentaPort;
    private final SaveCuentaGooglePort saveCuentaPort;
    private final SaveSalaMeetPort saveSalaPort;
    private final AuditoriaGooglePort auditoriaPort;
    private final AuditoriaDeGoogle auditoria;
    private final ConsultarPersonasPort personasPort;
    private final ConfiguracionDeGooglePort configuracion;
    private final Clock clock;

    CuentaDeGoogleService(AccesoEventoService accesoEventoService, AutorizacionGooglePort autorizacion,
                          ConexionesEnCursoPort conexiones, LoadCuentaGooglePort loadCuentaPort,
                          SaveCuentaGooglePort saveCuentaPort, SaveSalaMeetPort saveSalaPort,
                          AuditoriaGooglePort auditoriaPort, AuditoriaDeGoogle auditoria,
                          ConsultarPersonasPort personasPort, ConfiguracionDeGooglePort configuracion, Clock clock) {
        this.accesoEventoService = accesoEventoService;
        this.autorizacion = autorizacion;
        this.conexiones = conexiones;
        this.loadCuentaPort = loadCuentaPort;
        this.saveCuentaPort = saveCuentaPort;
        this.saveSalaPort = saveSalaPort;
        this.auditoriaPort = auditoriaPort;
        this.auditoria = auditoria;
        this.personasPort = personasPort;
        this.configuracion = configuracion;
        this.clock = clock;
    }

    @Override
    public String iniciarConexion(UserId actor) {
        accesoEventoService.requireAdministraElCalendario(actor);
        if (!configuracion.estaConfigurada()) {
            throw new IllegalStateException(SIN_CREDENCIALES);
        }
        Consentimiento consentimiento = autorizacion.pedirConsentimiento();
        conexiones.guardar(HuellaDeSecreto.de(consentimiento.state()), actor, consentimiento.codeVerifier(),
                clock.now().plus(VIGENCIA_DE_LA_CONEXION));
        return consentimiento.url();
    }

    @Override
    public ResultadoDeLaConexion completarConexion(String codigo, String state, String errorDeGoogle) {
        try {
            if (errorDeGoogle != null && !errorDeGoogle.isBlank()) {
                return new ResultadoDeLaConexion(false, SIN_PERMISO_DEL_USUARIO);
            }
            if (esVacio(codigo) || esVacio(state)) {
                return new ResultadoDeLaConexion(false, RESPUESTA_INCOMPLETA);
            }
            ConexionEnCurso conexion = conexiones.consumir(HuellaDeSecreto.de(state), clock.now()).orElse(null);
            if (conexion == null) {
                return new ResultadoDeLaConexion(false, CONEXION_VENCIDA);
            }
            return conectar(autorizacion.canjear(codigo, conexion.codeVerifier()), conexion.actor());
        } catch (RuntimeException ex) {
            // Nunca el motivo de Google ni el codigo: solo de que clase fue el fallo.
            log.warn("[calendar.CuentaDeGoogleService] no se pudo completar la conexion: {}", ex.getClass().getSimpleName());
            return new ResultadoDeLaConexion(false, NO_SE_PUDO);
        }
    }

    private ResultadoDeLaConexion conectar(PermisoConcedido permiso, UserId actor) {
        String faltante = permisoQueFalta(permiso.permisosConcedidos());
        if (faltante != null) {
            autorizacion.revocar(permiso.refreshToken());
            return new ResultadoDeLaConexion(false, faltante);
        }
        String esperado = configuracion.correoEsperado();
        if (!esVacio(esperado) && !esperado.trim().equalsIgnoreCase(permiso.correo())) {
            autorizacion.revocar(permiso.refreshToken());
            return new ResultadoDeLaConexion(false, OTRA_CUENTA);
        }
        Instant ahora = clock.now();
        CuentaDeGoogle cuenta = CuentaDeGoogle.conectar(permiso.correo(), permiso.refreshToken(), permiso.permisosConcedidos(), actor, ahora);
        cuenta.fijarCalendario(calendarioPara(cuenta, permiso.refreshToken()));
        saveCuentaPort.guardar(cuenta);
        saveSalaPort.reencolarLoPendiente(ahora);
        auditoria.registrar(AccionDeAuditoriaGoogle.CONECTAR, actor, null, true, null, auditoria.nuevaCorrelacion());
        return new ResultadoDeLaConexion(true, CONECTADA);
    }

    /** El calendario propio «Renaser 90 días» (FR-034), reutilizando el de antes si es la misma cuenta; con el permiso reducido no se puede, y se usa el principal. */
    private String calendarioPara(CuentaDeGoogle nueva, String refreshToken) {
        if (!nueva.tienePermiso(CuentaDeGoogle.PERMISO_CALENDARIO_PROPIO)) {
            return CuentaDeGoogle.CALENDARIO_PRINCIPAL;
        }
        String anterior = loadCuentaPort.cargar()
                .filter(c -> c.correo().equalsIgnoreCase(nueva.correo()))
                .map(CuentaDeGoogle::calendarioId).orElse(null);
        return anterior != null && !CuentaDeGoogle.CALENDARIO_PRINCIPAL.equals(anterior)
                ? anterior : autorizacion.crearCalendarioPropio(refreshToken, NOMBRE_DEL_CALENDARIO);
    }

    @Override
    public void desconectar(UserId actor) {
        accesoEventoService.requireAdministraElCalendario(actor);
        CuentaDeGoogle cuenta = loadCuentaPort.cargar().orElse(null);
        if (cuenta == null) {
            return;
        }
        try {
            autorizacion.revocar(cuenta.refreshToken());
        } catch (RuntimeException ex) {
            log.warn("[calendar.CuentaDeGoogleService] Google no confirmo la revocacion: {}", ex.getClass().getSimpleName());
        }
        saveCuentaPort.borrar();
        saveSalaPort.detenerLoPendiente(RevocacionDeLaCuenta.MOTIVO, clock.now());
        auditoria.registrar(AccionDeAuditoriaGoogle.DESCONECTAR, actor, null, true, null, auditoria.nuevaCorrelacion());
    }

    @Override
    public EstadoDeLaCuentaVista estado(UserId actor) {
        accesoEventoService.requireAdministraElCalendario(actor);
        List<RegistroDeAuditoriaGoogle> registros = auditoriaPort.ultimos(ACCIONES_A_LA_VISTA);
        List<AccionVista> acciones = accionesConNombre(registros);
        return loadCuentaPort.cargar()
                .map(c -> new EstadoDeLaCuentaVista(configuracion.estaConfigurada(), true, c.correo(), c.estado(), c.conectadaEn(),
                        c.ultimoUsoEn(), c.ultimoError(), acciones))
                .orElseGet(() -> new EstadoDeLaCuentaVista(configuracion.estaConfigurada(), false, null, null, null, null, null, acciones));
    }

    private List<AccionVista> accionesConNombre(List<RegistroDeAuditoriaGoogle> registros) {
        Set<UserId> ids = registros.stream().map(RegistroDeAuditoriaGoogle::actor).filter(a -> a != null).collect(Collectors.toSet());
        Map<UserId, Persona> personas = personasPort.porIds(ids);
        List<AccionVista> acciones = new ArrayList<>();
        for (RegistroDeAuditoriaGoogle r : registros) {
            String quien = r.actor() == null ? "El sistema" : personas.containsKey(r.actor()) ? personas.get(r.actor()).nombre() : "Alguien del equipo";
            acciones.add(new AccionVista(r.en(), r.accion(), quien, r.exito(), r.codigoHttp()));
        }
        return acciones;
    }

    /** Si Google no dio todos los permisos pedidos, la frase que dice cuál faltó; {@code null} si están todos. */
    private String permisoQueFalta(Set<String> concedidos) {
        Set<String> faltan = new LinkedHashSet<>(autorizacion.permisosObligatorios());
        faltan.removeAll(concedidos);
        if (faltan.isEmpty()) {
            return null;
        }
        String cuales = faltan.stream().map(CuentaDeGoogleService::nombreDelPermiso).collect(Collectors.joining(" y "));
        return "Google no dio todos los permisos. Falta: " + cuales + ". Vuelve a conectar y acepta todo lo que pide.";
    }

    private static String nombreDelPermiso(String permiso) {
        if (permiso.equals(CuentaDeGoogle.PERMISO_AJUSTES_DE_LA_SALA)) {
            return "cambiar quién puede entrar a la sala";
        }
        return "crear los eventos en el calendario";
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
