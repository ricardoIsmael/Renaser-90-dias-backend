package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /api/v1/admin/codigo-renaser-en-fichas}: si el mentor, la guía y el líder ven el Código Renaser (spec 034). */
record CodigoRenaserEnFichasRequest(@NotNull Boolean visible) {
}
