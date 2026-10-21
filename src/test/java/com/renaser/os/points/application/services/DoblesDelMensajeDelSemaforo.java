package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.out.semaforo.mensaje.CatalogoDeMensajesPort;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.MensajesElegidosPort;
import com.renaser.os.points.domain.model.semaforo.mensaje.CanalDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.ClaveDeLaEleccion;
import com.renaser.os.points.domain.model.semaforo.mensaje.EleccionesDelDia;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeDelSemaforo;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeVisto;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajesVistos;
import com.renaser.os.shared.domain.Clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Los dobles en memoria del mensaje del semáforo, compartidos por las pruebas de Hoy y del chat (D-277, D-281): un
 * reloj que se mueve, el catálogo y la tabla de lo elegido con la misma semántica que Postgres (PK por persona,
 * fecha, canal y grupo).
 */
final class DoblesDelMensajeDelSemaforo {

    private DoblesDelMensajeDelSemaforo() {
    }

    static final class RelojAjustable implements Clock {

        private Instant ahora;

        RelojAjustable(Instant ahora) {
            this.ahora = ahora;
        }

        void ajustar(Instant nuevo) {
            this.ahora = nuevo;
        }

        void avanzarUnDia() {
            this.ahora = ahora.plusSeconds(24 * 3600L);
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    static final class CatalogoEnMemoria implements CatalogoDeMensajesPort {

        private final List<MensajeDelSemaforo> mensajes = new ArrayList<>();

        void agregar(GrupoDelMensaje grupo, String id, String texto, String sticker) {
            mensajes.add(new MensajeDelSemaforo(id, grupo, texto, sticker, true));
        }

        @Override
        public List<MensajeDelSemaforo> activosDe(GrupoDelMensaje grupo) {
            return mensajes.stream().filter(m -> m.grupo() == grupo && m.activo())
                    .sorted(Comparator.comparing(MensajeDelSemaforo::id)).toList();
        }

        @Override
        public Optional<MensajeDelSemaforo> porId(String id) {
            return mensajes.stream().filter(m -> m.id().equals(id)).findFirst();
        }

        @Override
        public void recargar() {
            // En memoria no hay nada que descartar.
        }
    }

    /** La tabla: una fila por persona, fecha, canal y grupo; un {@code registrar} sobre una fila existente no la toca. */
    static final class ElegidosEnMemoria implements MensajesElegidosPort {

        private final Map<String, Fila> filas = new LinkedHashMap<>();
        private int escrituras;
        private int consultas;
        private int historialesLeidos;
        private String ganadorAjeno;

        private record Fila(ClaveDeLaEleccion clave, String mensajeId) {
        }

        void alRegistrarYaHabiaGanado(String mensajeId) {
            this.ganadorAjeno = mensajeId;
        }

        int escrituras() {
            return escrituras;
        }

        /** Cuántas veces se preguntó por lo elegido: una por lectura del mensaje. */
        int consultas() {
            return consultas;
        }

        /** En cuántas de esas el historial se trajo (solo cuando no había elección de hoy). */
        int historialesLeidos() {
            return historialesLeidos;
        }

        /** Lo elegido para Hoy, como {@code fecha|GRUPO|id}. */
        List<String> filas() {
            return filasDelCanal(CanalDelMensaje.HOY);
        }

        /** Lo elegido para el chat, como {@code fecha|GRUPO|id}. */
        List<String> filasDelChat() {
            return filasDelCanal(CanalDelMensaje.CHAT);
        }

        private List<String> filasDelCanal(CanalDelMensaje canal) {
            return filas.values().stream().filter(f -> f.clave().canal() == canal)
                    .map(f -> f.clave().fecha() + "|" + f.clave().grupo() + "|" + f.mensajeId()).toList();
        }

        /** Sembrar lo que otro canal o un día anterior ya dejó guardado, sin contarlo como escritura. */
        void sembrar(ClaveDeLaEleccion clave, String mensajeId) {
            filas.putIfAbsent(llave(clave), new Fila(clave, mensajeId));
        }

        private static String llave(ClaveDeLaEleccion c) {
            return c.persona() + "|" + c.fecha() + "|" + c.canal() + "|" + c.grupo();
        }

        @Override
        public EleccionesDelDia deLaPersona(ClaveDeLaEleccion clave) {
            consultas++;
            Fila propia = filas.get(llave(clave));
            if (propia != null) {
                return EleccionesDelDia.yaElegido(propia.mensajeId());
            }
            historialesLeidos++;
            return EleccionesDelDia.sinElegir(MensajesVistos.de(visibles(clave), clave.fecha(), clave.canal()));
        }

        /** Lo que la consulta real trae: los días anteriores de cualquier canal y lo de hoy del otro canal. */
        private List<MensajeVisto> visibles(ClaveDeLaEleccion clave) {
            return filas.values().stream()
                    .filter(f -> f.clave().persona().equals(clave.persona()))
                    .filter(f -> f.clave().fecha().isBefore(clave.fecha())
                            || (f.clave().fecha().equals(clave.fecha()) && f.clave().canal() != clave.canal()))
                    .map(f -> new MensajeVisto(f.clave().fecha(), f.mensajeId(), f.clave().canal())).toList();
        }

        @Override
        public String registrar(ClaveDeLaEleccion clave, String mensajeId, Instant ahora) {
            if (ganadorAjeno != null) {
                filas.putIfAbsent(llave(clave), new Fila(clave, ganadorAjeno));
            }
            if (filas.putIfAbsent(llave(clave), new Fila(clave, mensajeId)) == null) {
                escrituras++;
            }
            return filas.get(llave(clave)).mensajeId();
        }
    }
}
