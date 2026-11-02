package com.renaser.os.rag.application.ports.out.habitos;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Puerto propio de {@code rag} para todo lo que las herramientas del agente necesitan saber y
 * hacer con los habitos del dia. {@code registros_habito} es tabla de {@code habits} — por las
 * reglas de Modulith (D-41), {@code rag} no la consulta de frente: el adaptador que implementa
 * este puerto delega en {@code habits.api.AgendaDelDiaFinder}.
 *
 * <p>Mismo criterio que {@code LeerEntradasDiarioPort}: el puerto nombra la intencion de negocio
 * de ESTE modulo y define su propio {@link HabitoDelDia}, sin exponer el tipo de {@code habits}.
 * Asi {@code rag.application} no acopla su firma a un contrato ajeno, y el dia que ese contrato
 * cambie la traduccion queda contenida en el adaptador.
 *
 * <p><b>Lectura y escritura en el mismo puerto, a proposito.</b> No es un repositorio generico:
 * son las operaciones que el agente puede hacer con la agenda de un aprendiz, ni una mas
 * (ISP se cumple por lo chico del contrato, no por partirlo en dos interfaces de un metodo).
 * {@link #zonaDe} se sumo el 2026-09-23 para {@code consultar_tiempo_para_puntos}: decir los
 * plazos en la hora local del aprendiz. (Este parrafo decia "las tres operaciones"; ahora son
 * dos lecturas, la zona y una escritura.)
 */
public interface ConsultarAgendaHabitosPort {

    /** Vacio si el aprendiz no tiene habitos hoy. */
    List<HabitoDelDia> deHoyDe(UserId participanteId);

    /**
     * Marca uno como hecho, con las mismas guardas que la app.
     *
     * @return los puntos otorgados
     * @throws RuntimeException si el registro no existe, no es suyo, ya esta en estado terminal o
     *                          se le vencio la ventana — el caso de uso lo traduce a un
     *                          {@code ResultadoHerramienta.Fallo} legible, nunca lo deja subir
     */
    int completar(UserId actorId, UUID registroId);

    /**
     * La zona del aprendiz, la MISMA con la que {@code habits} calculo los plazos de
     * {@link #deHoyDe} (2026-09-23). Para decir esos instantes en su hora local y para traducir
     * una hora suya ("si lo hago a las 21:00") a un instante (regla 02).
     */
    ZoneId zonaDe(UserId participanteId);

    /**
     * @param puntosEnJuego  {@code null} cuando ya esta en estado terminal (nada en juego)
     * @param plazo          {@code null} cuando el habito no vence
     * @param exigeEvidencia si el habito pide evidencia. El agente NO puede subirla —el chat no
     *                       recibe fotos ni audios—: con el flag de botones le deja a la app la
     *                       tarjeta de la camara ({@code proponer_registrar_con_foto}, D-171); sin
     *                       el, lo dice y manda a la pantalla de Hoy. Completar sigue funcionando
     *                       igual con o sin ella: quien completa desde la app tampoco la entrega en
     *                       ese paso
     * @param tramos         la escala de puntos del habito hasta el {@code plazo}, ya resuelta por
     *                       {@code habits} (2026-09-23). Vacia si no vence o ya no esta en juego.
     *                       {@code rag} solo ubica instantes en ella: nunca reconstruye la escala
     * @param claveSistema   la clave del habito de catalogo ({@code DAILY_CLASS}...), {@code null}
     *                       en los personales (2026-09-26, D-171). Nunca se decide por el titulo
     * @param tituloDelPrograma el titulo del catalogo cuando la persona renombro el habito (D-133),
     *                       {@code null} si no lo renombro (E-290). Para que el modelo una "jugo
     *                       verde" con "Batido de papaya". Nunca trae el motivo del renombre
     * @param horaInicio     la hora de hoy a la que le toca (su preferencia si la tiene), {@code null}
     *                       sin horario (E-455): "lo que falta" se ordena por el horario de la persona
     * @param dimension      la categoria del habito, con el nombre que ve la persona en Training (Cuerpo, Mente...; las de
     *                       su formacion, spec 038); {@code null} si no se sabe (E-455): "te faltan 27: Cuerpo 13, Mente 8..."
     * @param habitoId       el habito (catalogo o propio) del que es el registro, {@code null} si no se sabe (spec 038): la app apaga con el
     *                       las alarmas de ese habito cuando SER lo cumple
     * @param fecha          el dia de este registro (su {@code fechaEjecucion}), {@code null} si no se sabe (spec 038)
     * @param medicion       la medicion del habito si mide un numero por dia (hoy KILOMETROS DIARIOS), {@code null} en los demas (spec 037, T021 de la 038)
     */
    record HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego, Integer puntosMaximos,
                         Instant plazo, boolean exigeEvidencia, List<TramoPuntos> tramos, String claveSistema,
                         String tituloDelPrograma, LocalTime horaInicio, String dimension, UUID habitoId, LocalDate fecha,
                         MedicionDelHabito medicion) {

        /** La Clase diaria se cierra con su resumen, nunca por el camino generico ni con una foto. */
        public static final String CLAVE_CLASE_DIARIA = "DAILY_CLASS";

        /** Los tres RITUAL TIERRA - AGUA - FUEGO (V69): los unicos que preguntan "¿Que sentiste?" (D-172). */
        public static final java.util.Set<String> CLAVES_DE_RITUAL =
                java.util.Set.of("RITUAL_MORNING", "RITUAL_MIDDAY", "RITUAL_NIGHT");

        public HabitoDelDia {
            tramos = tramos == null ? List.of() : List.copyOf(tramos);
        }

        /** Sin medicion (lo que habia antes de la spec 037 en SER). */
        public HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego, Integer puntosMaximos,
                            Instant plazo, boolean exigeEvidencia, List<TramoPuntos> tramos, String claveSistema,
                            String tituloDelPrograma, LocalTime horaInicio, String dimension, UUID habitoId, LocalDate fecha) {
            this(registroId, titulo, estado, puntosEnJuego, puntosMaximos, plazo, exigeEvidencia, tramos, claveSistema,
                    tituloDelPrograma, horaInicio, dimension, habitoId, fecha, null);
        }

        /** Sin el habito ni el dia (lo que habia antes de la spec 038). */
        public HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego, Integer puntosMaximos,
                            Instant plazo, boolean exigeEvidencia, List<TramoPuntos> tramos, String claveSistema,
                            String tituloDelPrograma, LocalTime horaInicio, String dimension) {
            this(registroId, titulo, estado, puntosEnJuego, puntosMaximos, plazo, exigeEvidencia, tramos, claveSistema,
                    tituloDelPrograma, horaInicio, dimension, null, null);
        }

        /** Sin hora ni dimension (lo que habia antes de E-455). */
        public HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego,
                            Integer puntosMaximos, Instant plazo, boolean exigeEvidencia, List<TramoPuntos> tramos,
                            String claveSistema, String tituloDelPrograma) {
            this(registroId, titulo, estado, puntosEnJuego, puntosMaximos, plazo, exigeEvidencia, tramos,
                    claveSistema, tituloDelPrograma, null, null);
        }

        /** Sin renombre: el titulo es el del programa. */
        public HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego,
                            Integer puntosMaximos, Instant plazo, boolean exigeEvidencia, List<TramoPuntos> tramos,
                            String claveSistema) {
            this(registroId, titulo, estado, puntosEnJuego, puntosMaximos, plazo, exigeEvidencia, tramos,
                    claveSistema, null);
        }

        /** Sin clave de sistema: para quien no la necesita. */
        public HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego,
                            Integer puntosMaximos, Instant plazo, boolean exigeEvidencia, List<TramoPuntos> tramos) {
            this(registroId, titulo, estado, puntosEnJuego, puntosMaximos, plazo, exigeEvidencia, tramos, null, null);
        }

        /** Sin escala: para quien no necesita los tramos (las tres herramientas originales). */
        public HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego,
                            Integer puntosMaximos, Instant plazo, boolean exigeEvidencia) {
            this(registroId, titulo, estado, puntosEnJuego, puntosMaximos, plazo, exigeEvidencia, List.of(), null,
                    null);
        }

        /**
         * Pide foto y se registra con la camara de la app (D-171). La Clase diaria tambien pide
         * evidencia en el catalogo, pero se cierra con su resumen: no entra.
         */
        public boolean seRegistraConFoto() {
            return exigeEvidencia && !CLAVE_CLASE_DIARIA.equals(claveSistema);
        }

        /**
         * Es uno de los tres rituales (D-172). Desde el 2026-10-31 (spec 038) NO se registran con foto: la persona <b>escribe</b> como se siente
         * ("¿Como te sientes despues de realizar el ritual?"), en el mismo registro por texto de Training. Antes la foto iba seguida de "¿Que sentiste?".
         */
        public boolean preguntaQueSintio() {
            return claveSistema != null && CLAVES_DE_RITUAL.contains(claveSistema);
        }

        /** Se registra escribiendo, sin camara: los rituales. */
        public boolean seRegistraEscribiendo() {
            return preguntaQueSintio();
        }

        /** Mide kilometros por dia (KILOMETROS DIARIOS): lleva meta y lo registrado hoy. */
        public boolean mideKilometros() {
            return medicion != null && medicion.esKilometros();
        }

        /**
         * Spec 037 (T021 de la 038): los kilometros se miden SOLOS con los pasos que la persona ya conecto en su telefono (Health Connect). Entonces SER no pide foto
         * ni captura: la manda al «Registrar» de la fila de Training, que cierra el habito con la lectura. Es la mejor pista que tiene el servidor
         * ({@link MedicionDelHabito#conPasosConectados()}): el permiso vive en el telefono.
         */
        public boolean seMideSolo() {
            return mideKilometros() && medicion.conPasosConectados();
        }

        /** Un habito con puntos en juego es, por definicion, uno que todavia se puede entregar. */
        public boolean sigueEnJuego() {
            return puntosEnJuego != null;
        }
    }

    /**
     * La medicion de un habito medible, como la ve SER (spec 037; T021 de la 038).
     *
     * @param unidad             {@code KILOMETROS}
     * @param meta               los km del dia; {@code null} si no se sabe
     * @param valorDelDia        lo ya registrado hoy; {@code null} si todavia nada (lo que la persona va caminando en vivo lo ve la app, no el servidor)
     * @param origenDelDia       {@code MANUAL} (captura) o {@code HEALTH_CONNECT}; {@code null} mientras no hay registro
     * @param fuenteDelDia       la fuente que la persona lee («Galaxy Watch»), solo en la lectura automatica
     * @param conPasosConectados si ya tiene algun dia medido por el telefono: SER no le pide foto
     */
    record MedicionDelHabito(String unidad, BigDecimal meta, BigDecimal valorDelDia, String origenDelDia, String fuenteDelDia,
                             boolean conPasosConectados) {

        public static final String KILOMETROS = "KILOMETROS";

        public boolean esKilometros() {
            return KILOMETROS.equals(unidad);
        }
    }

    /** Entregar antes de {@code hasta} (y despues del tramo anterior) paga {@code puntos}. */
    record TramoPuntos(Instant hasta, int puntos) {
    }
}
