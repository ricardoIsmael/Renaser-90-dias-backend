package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.domain.model.registro.PuntosEnJuego;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Hueco #10 — mismos campos que {@link RegistroHabitoResponse} (no se rompe el contrato
 * existente) MAS el catalogo resuelto: titulo, tipo, guia y horario.
 *
 * <p><b>Agregado 2026-09-05 (pedido del dueno, "puntos en juego"):</b> los tres campos
 * {@code puntosEnJuego} / {@code puntosMaximos} / {@code plazoEvidencia}. Son campos NUEVOS,
 * nunca un reemplazo: {@code puntosOtorgados} sigue significando lo mismo (lo que YA se cobro,
 * 0 mientras no este completado) y ningun cliente existente cambia de comportamiento.
 *
 * <ul>
 *   <li>{@code puntosEnJuego}: lo que paga completarlo en este momento, con la escala real de
 *   D-97 (10 a tiempo o dentro de la extension; de 10 a 5 decayendo en los 10 minutos de
 *   gracia). {@code null} si el registro ya esta en estado terminal.</li>
 *   <li>{@code puntosMaximos}: el techo de la escala, para que la pantalla pueda decir "6 de
 *   10" sin conocer la constante ni reimplementarla.</li>
 *   <li>{@code plazoEvidencia}: el instante exacto en que el registro se bloquea. Es lo que le
 *   permite al movil mostrar la cuenta regresiva y ordenar "el proximo a vencer" sin recalcular
 *   ninguna ventana ni conocer la zona horaria del aprendiz. {@code null} si el habito no tiene
 *   ninguna hora configurada — ese no vence nunca.</li>
 * </ul>
 */
public record RegistroHabitoConCatalogoResponse(String id, UUID habitoId, LocalDate fechaEjecucion, int diaPrograma,
                                                  String tipoDia, boolean esOpcional, String estado,
                                                  int puntosOtorgados, String respuestaTexto,
                                                  Integer calificacionProductividad, Instant completadoEn,
                                                  String tituloHabito, String tipoHabito, GuiaResumenResponse guia,
                                                  LocalTime horaDisparo, LocalTime horaLimite, Integer puntosEnJuego,
                                                  Integer puntosMaximos, Instant plazoEvidencia) {

    public static RegistroHabitoConCatalogoResponse from(TrackDelDiaConCatalogo vista) {
        var r = vista.registro();
        PuntosEnJuego enJuego = vista.puntosEnJuego();
        return new RegistroHabitoConCatalogoResponse(r.id().toString(), r.habitoId().value(), r.fechaEjecucion(),
                r.diaPrograma(), r.tipoDia().name(), r.esOpcional(), r.estado().name(), r.puntosOtorgados(),
                r.respuestaTexto(), r.calificacionProductividad(), r.completadoEn(), vista.tituloHabito(),
                vista.tipoHabito() != null ? vista.tipoHabito().name() : null,
                vista.guia() != null ? GuiaResumenResponse.from(vista.guia()) : null, vista.horaDisparo(),
                vista.horaLimite(), enJuego != null ? enJuego.siCompletaAhora() : null,
                enJuego != null ? enJuego.maximo() : null, enJuego != null ? enJuego.plazo() : null);
    }
}
