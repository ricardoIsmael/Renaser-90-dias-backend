package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.ZoneId;

/**
 * La zona horaria de quien acompaña (mentor o guía), en UN solo lugar: la de su programa si participa; si no, la de la cohorte de su primer
 * grupo vigente; y America/Lima por defecto. La usan los barridos que avisan «a las 19:00 / 20:00 de SU zona» ({@code mentoring} y
 * {@code leadership}) para no repetir la regla.
 */
public interface ZonaDelAcompananteFinder {

    ZoneId zonaDe(UserId acompanante, Instant ahora);
}
