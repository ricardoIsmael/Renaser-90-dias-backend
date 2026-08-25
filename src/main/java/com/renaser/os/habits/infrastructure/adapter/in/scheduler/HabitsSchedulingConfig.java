package com.renaser.os.habits.infrastructure.adapter.in.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Mismo patron que `points.PointsSchedulingConfig` (D-P4): @EnableScheduling es un flag global
 * de Spring, seguro de declarar en mas de un modulo (idempotente). */
@Configuration
@EnableScheduling
class HabitsSchedulingConfig {
}
