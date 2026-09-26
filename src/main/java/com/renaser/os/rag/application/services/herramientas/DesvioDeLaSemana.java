package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarHabitosVencidosPort.HabitoVencido;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.HabitoDelPlan;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.ProgresoDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
import com.renaser.os.rag.application.ports.out.semaforo.ConsultarSemaforoDelAprendizPort.SemaforoDelAprendiz;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que junto {@link ConsultarDesvioDeLaSemanaHerramienta} de cada fuente (D-177). Cada campo en
 * {@code null} es una fuente que no se pudo leer (y figura en {@code faltantes}) o que no aplica.
 *
 * @param desde       primer dia de la semana que se mira (el de la semana de programa, o el lunes)
 * @param hoy         hoy en la zona de la persona
 * @param vencidos    habitos vencidos sin cumplir en los dias ya terminados ({@code desde} a ayer)
 * @param pausados    habitos en pausa hoy
 * @param semaforo    {@code null} tambien si la persona no se mide ({@code semaforoNoAplica})
 * @param faltantes   lo que no se pudo leer, en palabras para la persona
 */
record DesvioDeLaSemana(LocalDate desde, LocalDate hoy, ProgresoDeLaSemana progreso, RocasDeLaSemana objetivos,
                        List<HabitoVencido> vencidos, List<HabitoDelPlan> pausados, SemaforoDelAprendiz semaforo,
                        boolean semaforoNoAplica, List<String> faltantes) {
}
