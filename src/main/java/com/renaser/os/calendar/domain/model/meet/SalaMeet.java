package com.renaser.os.calendar.domain.model.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * La sala de Meet de UNA fecha de un evento (spec 040, tabla {@code eventos_meet}). Un evento que no se repite tiene una; uno que se
 * repite tiene una por fecha de la ventana, cada una con su propio evento de Google y su propio enlace (decisión 4 del dueño).
 *
 * <p>Qué dice cada estado está en {@link EstadoSalaMeet}. Solo cambia por métodos con nombre de intención. El motivo de un fallo es un
 * texto corto y sin datos personales: nunca el cuerpo de la respuesta de Google, un token ni el enlace.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class SalaMeet {

    public static final int LARGO_MAXIMO_DEL_ERROR = 200;

    private final UUID id;
    private final EventoId eventoId;
    private LocalDate fechaOcurrencia;
    private final String idGoogle;
    private String enlace;
    private EstadoSalaMeet estado;
    private AccesoSalaMeet accesoElegido;
    private AccesoSalaMeet accesoConfirmado;
    private int intentos;
    private Instant proximoIntento;
    private String ultimoError;
    private final Instant creadaEn;
    private Instant actualizadaEn;
    private long version;
    /** La grabación, la transcripción y las notas que Google dejó encendidas (FR-036); {@link ArtefactosDeLaSala#NADA} si todavía no se pidieron. */
    private ArtefactosDeLaSala artefactos;

    /** Una sala que todavía no se le pidió a Google: nace {@code PENDIENTE} y lista para el primer intento. */
    public static SalaMeet nueva(UUID id, EventoId eventoId, LocalDate fecha, String idGoogle, AccesoSalaMeet acceso,
                                 Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(eventoId, "eventoId es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");
        Objects.requireNonNull(idGoogle, "idGoogle es obligatorio");
        Objects.requireNonNull(acceso, "acceso es obligatorio");
        return new SalaMeet(id, eventoId, fecha, idGoogle, null, EstadoSalaMeet.PENDIENTE, acceso, null, 0, ahora, null,
                ahora, ahora, 0L, ArtefactosDeLaSala.NADA);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila existente sin revalidar. */
    public static SalaMeet rehydrate(UUID id, EventoId eventoId, LocalDate fecha, String idGoogle, String enlace,
                                     EstadoSalaMeet estado, AccesoSalaMeet accesoElegido,
                                     AccesoSalaMeet accesoConfirmado, int intentos, Instant proximoIntento,
                                     String ultimoError, Instant creadaEn, Instant actualizadaEn, long version) {
        return rehydrate(id, eventoId, fecha, idGoogle, enlace, estado, accesoElegido, accesoConfirmado, intentos, proximoIntento, ultimoError,
                creadaEn, actualizadaEn, version, ArtefactosDeLaSala.NADA);
    }

    /** Igual, con la grabación, la transcripción y las notas que Google confirmó (V171). */
    public static SalaMeet rehydrate(UUID id, EventoId eventoId, LocalDate fecha, String idGoogle, String enlace,
                                     EstadoSalaMeet estado, AccesoSalaMeet accesoElegido,
                                     AccesoSalaMeet accesoConfirmado, int intentos, Instant proximoIntento,
                                     String ultimoError, Instant creadaEn, Instant actualizadaEn, long version,
                                     ArtefactosDeLaSala artefactos) {
        return new SalaMeet(id, eventoId, fecha, idGoogle, enlace, estado, accesoElegido, accesoConfirmado, intentos,
                proximoIntento, ultimoError, creadaEn, actualizadaEn, version, artefactos == null ? ArtefactosDeLaSala.NADA : artefactos);
    }

    /**
     * Lo que Google dejó encendido de la grabación, la transcripción y las notas al leer la sala de vuelta (FR-036). No cuenta como trabajo
     * pendiente ni genera reintentos: si la cuenta no tiene el plan, reintentar no lo cambia (FR-037).
     */
    public void registrarArtefactos(ArtefactosDeLaSala confirmados, Instant ahora) {
        this.artefactos = confirmados == null ? ArtefactosDeLaSala.NADA : confirmados;
        this.actualizadaEn = ahora;
    }

    /** Todavía no se le pidió a Google la grabación ni lo demás de esta sala. */
    public boolean artefactosSinPedir() {
        return estado == EstadoSalaMeet.CREADO && !artefactos.algo();
    }

    /** Google creó la sala: se guarda su enlace y se olvidan los fallos. */
    public void completarCon(String enlaceDeLaSala, Instant ahora) {
        this.enlace = Objects.requireNonNull(enlaceDeLaSala, "enlace es obligatorio");
        this.estado = EstadoSalaMeet.CREADO;
        this.intentos = 0;
        this.proximoIntento = null;
        this.ultimoError = null;
        this.actualizadaEn = ahora;
    }

    /** Google respondió {@code pending}: el enlace todavía no está; se vuelve a mirar en {@code proximo}. */
    public void quedarPendiente(Instant ahora, Instant proximo) {
        this.estado = EstadoSalaMeet.PENDIENTE;
        this.proximoIntento = proximo;
        this.actualizadaEn = ahora;
    }

    /** No se pudo crear. {@code proximo == null}: ya no hay más intentos. */
    public void fallarAlCrear(String motivo, Instant ahora, Instant proximo) {
        this.estado = EstadoSalaMeet.FALLIDO;
        registrarFallo(motivo, ahora, proximo);
    }

    /** No se pudo actualizar ni borrar en Google: Renaser ya cambió y se reintenta en segundo plano. */
    public void fallarAlSincronizar(String motivo, Instant ahora, Instant proximo) {
        this.estado = EstadoSalaMeet.PENDIENTE_DE_SINCRONIZAR;
        registrarFallo(motivo, ahora, proximo);
    }

    /** No se pudo dejar la sala abierta o con permiso: el evento y el enlace se conservan (FR-023). */
    public void fallarElAcceso(String motivo, Instant ahora, Instant proximo) {
        registrarFallo(motivo, ahora, proximo);
    }

    /** Renaser cambió (título, hora, duración…): Google tiene que enterarse. Solo tiene sentido en una sala que ya existe. */
    public void pedirSincronizar(Instant ahora) {
        if (estado == EstadoSalaMeet.CREADO) {
            this.estado = EstadoSalaMeet.PENDIENTE_DE_SINCRONIZAR;
            this.intentos = 0;
            this.proximoIntento = ahora;
            this.actualizadaEn = ahora;
        }
    }

    /** Google ya tiene el cambio. */
    public void sincronizada(Instant ahora) {
        this.estado = EstadoSalaMeet.CREADO;
        this.intentos = 0;
        this.proximoIntento = null;
        this.ultimoError = null;
        this.actualizadaEn = ahora;
    }

    /** El evento de Google se borró. La fila queda como lápida y no guarda el enlace. */
    public void marcarBorrada(Instant ahora) {
        this.estado = EstadoSalaMeet.BORRADO;
        this.enlace = null;
        this.accesoConfirmado = null;
        this.artefactos = ArtefactosDeLaSala.NADA;
        this.intentos = 0;
        this.proximoIntento = null;
        this.ultimoError = null;
        this.actualizadaEn = ahora;
    }

    /** La fecha volvió a la ventana después de haberse borrado: se pide una sala nueva con el mismo id de Google. */
    public void reactivar(AccesoSalaMeet acceso, Instant ahora) {
        this.estado = EstadoSalaMeet.PENDIENTE;
        this.enlace = null;
        this.accesoElegido = acceso;
        this.accesoConfirmado = null;
        this.artefactos = ArtefactosDeLaSala.NADA;
        this.intentos = 0;
        this.proximoIntento = ahora;
        this.ultimoError = null;
        this.actualizadaEn = ahora;
    }

    /** Administración cambió quién puede entrar. Lo que Google confirmó queda como estaba hasta que lo aplique. */
    public void elegirAcceso(AccesoSalaMeet acceso, Instant ahora) {
        if (this.accesoElegido != acceso) {
            this.accesoElegido = acceso;
            this.intentos = 0;
            this.proximoIntento = ahora;
            this.actualizadaEn = ahora;
        }
    }

    /** Lo que Google dijo al leer la sala de vuelta (un 200 no prueba que quedó). {@code null}: no se pudo confirmar. */
    public void registrarAccesoConfirmado(AccesoSalaMeet confirmado, Instant ahora) {
        this.accesoConfirmado = confirmado;
        if (confirmado == accesoElegido) {
            this.intentos = 0;
            this.proximoIntento = null;
            this.ultimoError = null;
        }
        this.actualizadaEn = ahora;
    }

    /** Un evento de un solo día que se mueve a otra fecha conserva su sala: solo cambia la fecha con que se la busca. */
    public void moverA(LocalDate nuevaFecha, Instant ahora) {
        this.fechaOcurrencia = Objects.requireNonNull(nuevaFecha, "nuevaFecha es obligatoria");
        this.actualizadaEn = ahora;
    }

    /** Después de reconectar la cuenta: lo que se había rendido vuelve a intentarse ya. */
    public void reencolar(Instant ahora) {
        if (estado.faltaTrabajo() || accesoPendiente()) {
            this.intentos = 0;
            this.proximoIntento = ahora;
            this.actualizadaEn = ahora;
        }
    }

    /** La sala existe, pero Google todavía no la tiene como se eligió (abierta o con permiso). */
    public boolean accesoPendiente() {
        return estado == EstadoSalaMeet.CREADO && accesoConfirmado != accesoElegido;
    }

    /** Si hay algo que decirle a Google, ya toca y quedan intentos. */
    public boolean tocaReintentar(Instant ahora) {
        boolean hayTrabajo = estado.faltaTrabajo() || accesoPendiente();
        return hayTrabajo && proximoIntento != null && !ahora.isBefore(proximoIntento) && intentos < PoliticaDeReintentos.MAXIMO_DE_INTENTOS;
    }

    private void registrarFallo(String motivo, Instant ahora, Instant proximo) {
        this.intentos++;
        this.ultimoError = limpiar(motivo);
        this.proximoIntento = proximo;
        this.actualizadaEn = ahora;
    }

    private static String limpiar(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return null;
        }
        String sinSaltos = motivo.replaceAll("\\s+", " ").trim();
        return sinSaltos.length() <= LARGO_MAXIMO_DEL_ERROR ? sinSaltos : sinSaltos.substring(0, LARGO_MAXIMO_DEL_ERROR);
    }

    @Override
    public String toString() {
        return "SalaMeet[" + eventoId + ", " + fechaOcurrencia + ", " + estado + "]";
    }
}
