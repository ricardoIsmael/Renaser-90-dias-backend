package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.meet.ConfiguracionDeGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleNoConectadaException;
import com.renaser.os.calendar.application.ports.out.meet.CuentaGoogleRevocadaException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleNoRespondeException;
import com.renaser.os.calendar.application.ports.out.meet.GoogleRechazoException;
import com.renaser.os.calendar.application.ports.out.meet.PedidoDeSala;
import com.renaser.os.calendar.application.ports.out.meet.ResultadoDeCoanfitriones;
import com.renaser.os.calendar.application.ports.out.meet.ResultadoDeSala;
import com.renaser.os.calendar.application.ports.out.meet.SalaDeReunionPort;
import com.renaser.os.calendar.application.ports.out.meet.SaveSalaMeetPort;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.ArtefactosDeLaSala;
import com.renaser.os.calendar.domain.model.meet.EstadoDeCoanfitriones;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.PlanDeSalas.OcurrenciaDeseada;
import com.renaser.os.calendar.domain.model.meet.PoliticaDeReintentos;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Cada cosa concreta que Renaser le pide a Google para UNA fecha —crear su sala, actualizarla, dejarla abierta o con permiso, borrarla— y cómo
 * queda la fila después (spec 040). Nada de lo que falla en Google se propaga: queda anotado en la fila, con su motivo y su próximo intento
 * (FR-008), y el evento de Renaser sigue como estaba (SC-003). La única excepción que corta el recorrido es {@link CuentaInutilizable}: si la
 * cuenta de Google ya no sirve no tiene sentido seguir con las demás fechas.
 */
@Component
class PasosConGoogle {

    private static final Logger log = LoggerFactory.getLogger(PasosConGoogle.class);

    /** Para cortar el recorrido de una sincronización: la cuenta no está conectada o Google acaba de revocarla. Sin traza: no es un error de programa. */
    static final class CuentaInutilizable extends RuntimeException {
        CuentaInutilizable() {
            super(RevocacionDeLaCuenta.MOTIVO, null, false, false);
        }
    }

    /** Quién pide y bajo qué operación quedan sus filas de auditoría; {@code actor == null} es el sistema (el proceso en segundo plano). */
    record Operacion(UserId actor, String correlacion) {
    }

    private final SalaDeReunionPort google;
    private final SaveSalaMeetPort saveSalaPort;
    private final AuditoriaDeGoogle auditoria;
    private final RevocacionDeLaCuenta revocacion;
    private final Clock clock;
    private final DoubleSupplier azar;
    private final ConfiguracionDeGooglePort configuracion;

    @Autowired
    PasosConGoogle(SalaDeReunionPort google, SaveSalaMeetPort saveSalaPort, AuditoriaDeGoogle auditoria,
                   RevocacionDeLaCuenta revocacion, Clock clock, ConfiguracionDeGooglePort configuracion) {
        this(google, saveSalaPort, auditoria, revocacion, clock, () -> ThreadLocalRandom.current().nextDouble(), configuracion);
    }

    PasosConGoogle(SalaDeReunionPort google, SaveSalaMeetPort saveSalaPort, AuditoriaDeGoogle auditoria,
                   RevocacionDeLaCuenta revocacion, Clock clock, DoubleSupplier azar, ConfiguracionDeGooglePort configuracion) {
        this.configuracion = configuracion;
        this.google = google;
        this.saveSalaPort = saveSalaPort;
        this.auditoria = auditoria;
        this.revocacion = revocacion;
        this.clock = clock;
        this.azar = azar;
    }

    /**
     * Pide la sala de esa fecha; si Google ya la tenía, toma la suya (FR-007). Después la deja abierta y le pide que grabe y transcriba sola.
     *
     * @param invitados quién va como invitado del evento de Calendar (FR-035); {@code null} = no tocar los que haya
     */
    void crear(SalaMeet sala, OcurrenciaDeseada fecha, ZoneId zona, List<String> invitados, Operacion operacion) {
        PedidoDeSala pedido = new PedidoDeSala(sala.idGoogle(), fecha.titulo(), fecha.inicio(), fecha.fin(), zona, invitados);
        try {
            ResultadoDeSala resultado = google.crear(pedido);
            Instant ahora = clock.now();
            if (resultado.lista()) {
                sala.completarCon(resultado.enlace(), ahora);
            } else {
                sala.quedarPendiente(ahora, ahora.plusSeconds(60));
            }
            saveSalaPort.guardar(sala);
            auditar(AccionDeAuditoriaGoogle.CREAR_SALA, sala, true, null, operacion);
        } catch (RuntimeException ex) {
            fallarAlCrear(sala, ex, operacion);
            return;
        }
        if (sala.estado() == EstadoSalaMeet.CREADO) {
            ajustarAcceso(sala, operacion);
        }
    }

    /** Google se entera del cambio de título, hora o duración. El enlace no cambia. */
    void actualizar(SalaMeet sala, OcurrenciaDeseada fecha, ZoneId zona, List<String> invitados, Operacion operacion) {
        PedidoDeSala pedido = new PedidoDeSala(sala.idGoogle(), fecha.titulo(), fecha.inicio(), fecha.fin(), zona, invitados);
        try {
            google.actualizar(pedido);
            sala.sincronizada(clock.now());
            saveSalaPort.guardar(sala);
            auditar(AccionDeAuditoriaGoogle.ACTUALIZAR_SALA, sala, true, null, operacion);
        } catch (RuntimeException ex) {
            fallarAlSincronizar(sala, ex, AccionDeAuditoriaGoogle.ACTUALIZAR_SALA, operacion);
            return;
        }
        if (sala.accesoPendiente()) {
            ajustarAcceso(sala, operacion);
        }
    }

    /** Borra el evento de Google. Que ya no exista cuenta como hecho. */
    void borrar(SalaMeet sala, Operacion operacion) {
        try {
            google.borrar(sala.idGoogle());
            sala.marcarBorrada(clock.now());
            saveSalaPort.guardar(sala);
            auditar(AccionDeAuditoriaGoogle.BORRAR_SALA, sala, true, null, operacion);
        } catch (RuntimeException ex) {
            fallarAlSincronizar(sala, ex, AccionDeAuditoriaGoogle.BORRAR_SALA, operacion);
        }
    }

    /**
     * Deja la sala como se eligió y la lee de vuelta (FR-023). Si Google no lo acepta o falta el permiso, el evento y el enlace se conservan: solo
     * queda anotado que lo elegido y lo confirmado no coinciden.
     */
    void ajustarAcceso(SalaMeet sala, Operacion operacion) {
        AccesoSalaMeet elegido = sala.accesoElegido();
        try {
            Optional<AccesoSalaMeet> confirmado = google.fijarAcceso(sala.enlace(), elegido);
            Instant ahora = clock.now();
            sala.registrarAccesoConfirmado(confirmado.orElse(null), ahora);
            if (confirmado.orElse(null) != elegido) {
                sala.fallarElAcceso("Google no dejó la sala como se eligió.", ahora,
                        PoliticaDeReintentos.proximoIntento(sala.intentos() + 1, ahora, azar.getAsDouble()));
            }
            saveSalaPort.guardar(sala);
            auditar(AccionDeAuditoriaGoogle.AJUSTAR_ACCESO, sala, confirmado.orElse(null) == elegido, null, operacion);
        } catch (RuntimeException ex) {
            fallarElAcceso(sala, ex, operacion);
            return;
        }
        pedirGrabacionSiHaceFalta(sala, operacion);
    }

    /**
     * Pide que la sala grabe, transcriba y tome notas sola (FR-036) y anota lo que Google dejó. Nunca falla ni deja trabajo pendiente: si la cuenta no
     * tiene el plan, la sala y el evento siguen como estaban y Administración ve el aviso (FR-037). Un fallo pasajero (red, 5xx) deja la sala «sin
     * pedir» para que el próximo ajuste lo intente otra vez; una cuenta revocada la detecta el siguiente pedido a Google.
     */
    void pedirGrabacionSiHaceFalta(SalaMeet sala, Operacion operacion) {
        ArtefactosDeLaSala pedidos = ArtefactosDeLaSala.pedir(configuracion.grabar(), configuracion.transcribir(), configuracion.tomarNotas());
        if (!pedidos.algo() || !sala.artefactosSinPedir()) {
            return;
        }
        try {
            ArtefactosDeLaSala confirmados = google.fijarArtefactos(sala.enlace(), pedidos);
            sala.registrarArtefactos(confirmados, clock.now());
            saveSalaPort.guardar(sala);
            auditar(AccionDeAuditoriaGoogle.AJUSTAR_GRABACION, sala, !confirmados.algunoRechazado(), null, operacion);
        } catch (GoogleRechazoException rechazo) {
            // Google dijo que no y ni siquiera dejó leer la sala: se anota como no disponible para no insistir en cada sincronización.
            sala.registrarArtefactos(new ArtefactosDeLaSala(pedidos.grabacion() == null ? null : Boolean.FALSE,
                    pedidos.transcripcion() == null ? null : Boolean.FALSE, pedidos.notas() == null ? null : Boolean.FALSE), clock.now());
            saveSalaPort.guardar(sala);
            auditar(AccionDeAuditoriaGoogle.AJUSTAR_GRABACION, sala, false, rechazo.codigoHttp(), operacion);
        } catch (RuntimeException pasajero) {
            Integer codigo = pasajero instanceof GoogleNoRespondeException nr ? nr.codigoHttp() : null;
            auditar(AccionDeAuditoriaGoogle.AJUSTAR_GRABACION, sala, false, codigo, operacion);
        }
    }

    static final String MOTIVO_SIN_CORREO = "No se encontró el correo de quien dirige la sesión.";
    static final String MOTIVO_NO_QUEDO = "Google no dejó a quien dirige la sesión como coanfitrión (código %d): falta el permiso, la sala no es "
            + "de la app o el correo no es de una cuenta de Google.";
    static final String MOTIVO_PARCIAL = "Google dejó como coanfitrión solo a %d de %d personas que dirigen la sesión (código %d).";

    /**
     * Hace coanfitrión de la sala a quien dirige la sesión, para que Meet grabe y transcriba solo (spec 040, FR-038: Google solo empieza a grabar
     * cuando entra el anfitrión o un coanfitrión). Con la opción apagada no llama a Google. Nunca falla ni deja trabajo pendiente: lo que Google
     * rechaza queda anotado en la sala con una frase fija —nunca correos ni texto de Google— y el evento sigue como estaba. Un fallo pasajero (red,
     * 5xx) deja la sala «sin pedir» para el próximo intento; una cuenta revocada la detecta el siguiente pedido.
     *
     * @param correos los correos de quien dirige: quien creó el evento y el mentor del grupo convocado
     */
    void pedirCoanfitriones(SalaMeet sala, List<String> correos, Operacion operacion) {
        if (!configuracion.coanfitriones() || !sala.coanfitrionesPorPedir()) {
            return;
        }
        if (correos.isEmpty()) {
            sala.registrarCoanfitriones(EstadoDeCoanfitriones.SIN_CORREO, MOTIVO_SIN_CORREO, clock.now());
            saveSalaPort.guardar(sala);
            return;
        }
        try {
            ResultadoDeCoanfitriones resultado = google.agregarCoanfitriones(sala.enlace(), correos);
            int codigo = resultado.codigoHttp() == null ? 0 : resultado.codigoHttp();
            if (resultado.quedaron() >= resultado.pedidos()) {
                sala.registrarCoanfitriones(EstadoDeCoanfitriones.OK, null, clock.now());
            } else if (resultado.quedaron() > 0) {
                sala.registrarCoanfitriones(EstadoDeCoanfitriones.PARCIAL,
                        MOTIVO_PARCIAL.formatted(resultado.quedaron(), resultado.pedidos(), codigo), clock.now());
            } else {
                sala.registrarCoanfitriones(EstadoDeCoanfitriones.FALLIDO, MOTIVO_NO_QUEDO.formatted(codigo), clock.now());
            }
            saveSalaPort.guardar(sala);
            auditar(AccionDeAuditoriaGoogle.AJUSTAR_COANFITRIONES, sala, resultado.quedaron() >= resultado.pedidos(),
                    resultado.codigoHttp(), operacion);
        } catch (GoogleRechazoException rechazo) {
            sala.registrarCoanfitriones(EstadoDeCoanfitriones.FALLIDO, MOTIVO_NO_QUEDO.formatted(rechazo.codigoHttp()), clock.now());
            saveSalaPort.guardar(sala);
            auditar(AccionDeAuditoriaGoogle.AJUSTAR_COANFITRIONES, sala, false, rechazo.codigoHttp(), operacion);
        } catch (CuentaGoogleRevocadaException | CuentaGoogleNoConectadaException cuenta) {
            clasificar(cuenta, operacion); // avisa la revocación; la sala queda sin pedir
        } catch (RuntimeException pasajero) {
            Integer codigo = pasajero instanceof GoogleNoRespondeException nr ? nr.codigoHttp() : null;
            auditar(AccionDeAuditoriaGoogle.AJUSTAR_COANFITRIONES, sala, false, codigo, operacion);
        }
    }

    // ─── Fallos ─────────────────────────────────────────────────────────────────

    private void fallarAlCrear(SalaMeet sala, RuntimeException ex, Operacion operacion) {
        Falla falla = clasificar(ex, operacion);
        Instant ahora = clock.now();
        sala.fallarAlCrear(falla.motivo(), ahora, falla.proximoIntento(sala.intentos() + 1, ahora, azar));
        saveSalaPort.guardar(sala);
        auditar(AccionDeAuditoriaGoogle.CREAR_SALA, sala, false, falla.codigoHttp(), operacion);
        if (falla.cortaElRecorrido()) {
            throw new CuentaInutilizable();
        }
    }

    private void fallarAlSincronizar(SalaMeet sala, RuntimeException ex, AccionDeAuditoriaGoogle accion, Operacion operacion) {
        Falla falla = clasificar(ex, operacion);
        Instant ahora = clock.now();
        sala.fallarAlSincronizar(falla.motivo(), ahora, falla.proximoIntento(sala.intentos() + 1, ahora, azar));
        saveSalaPort.guardar(sala);
        auditar(accion, sala, false, falla.codigoHttp(), operacion);
        if (falla.cortaElRecorrido()) {
            throw new CuentaInutilizable();
        }
    }

    private void fallarElAcceso(SalaMeet sala, RuntimeException ex, Operacion operacion) {
        Falla falla = clasificar(ex, operacion);
        Instant ahora = clock.now();
        sala.fallarElAcceso(falla.motivo(), ahora, falla.proximoIntento(sala.intentos() + 1, ahora, azar));
        saveSalaPort.guardar(sala);
        auditar(AccionDeAuditoriaGoogle.AJUSTAR_ACCESO, sala, false, falla.codigoHttp(), operacion);
        if (falla.cortaElRecorrido()) {
            throw new CuentaInutilizable();
        }
    }

    /** Qué fue lo que falló: reintentable (Google no puede ahora), definitivo (Google dijo que no) o la cuenta ya no sirve. */
    private Falla clasificar(RuntimeException ex, Operacion operacion) {
        if (ex instanceof CuentaGoogleRevocadaException || ex instanceof CuentaGoogleNoConectadaException) {
            revocacion.alRevocarse(operacion.actor(), operacion.correlacion());
            return new Falla(RevocacionDeLaCuenta.MOTIVO, null, false, true);
        }
        // El motivo que se guarda lo arma ESTE servicio con frases fijas y el código; nunca se copia el texto de la excepción (FR-011, SC-006):
        // aunque un adaptador pusiera ahí algo que dijo Google, un permiso o un enlace no puede llegar a la base, a la pantalla ni a un registro.
        if (ex instanceof GoogleNoRespondeException noResponde) {
            return new Falla(motivoSiGoogleNoResponde(noResponde.codigoHttp()), noResponde.codigoHttp(), true, false);
        }
        if (ex instanceof GoogleRechazoException rechazo) {
            return new Falla("Google no aceptó el pedido (código " + rechazo.codigoHttp() + ").", rechazo.codigoHttp(), false, false);
        }
        // Algo que no es de Google (un error nuestro): se anota sin detalle y se reintenta, porque puede ser pasajero.
        return new Falla("Falló algo al hablar con Google.", null, true, false);
    }

    private static String motivoSiGoogleNoResponde(int codigoHttp) {
        if (codigoHttp == 429) {
            return "Se llegó al límite de pedidos a Google. Se reintenta solo.";
        }
        if (codigoHttp <= 0) {
            return "No pudimos hablar con Google. Se reintenta solo.";
        }
        return "Google no pudo atender ahora (código " + codigoHttp + "). Se reintenta solo.";
    }

    /** @param reintentable si vale la pena volver a intentarlo solo; si no, queda esperando un «Reintentar» manual */
    private record Falla(String motivo, Integer codigoHttp, boolean reintentable, boolean cortaElRecorrido) {

        Instant proximoIntento(int intentos, Instant ahora, DoubleSupplier azar) {
            if (cortaElRecorrido || !reintentable) {
                return null;
            }
            return PoliticaDeReintentos.proximoIntento(intentos, ahora, azar.getAsDouble());
        }
    }

    private void auditar(AccionDeAuditoriaGoogle accion, SalaMeet sala, boolean exito, Integer codigoHttp, Operacion operacion) {
        if (!exito) {
            // Solo la acción y el código: nunca el texto de lo que dijo Google ni el enlace (SC-006).
            log.warn("[calendar.PasosConGoogle] {} no salio (codigo {}), correlacion {}", accion, codigoHttp, operacion.correlacion());
        }
        auditoria.registrar(accion, operacion.actor(), sala.eventoId().value(), exito, codigoHttp, operacion.correlacion());
    }
}
