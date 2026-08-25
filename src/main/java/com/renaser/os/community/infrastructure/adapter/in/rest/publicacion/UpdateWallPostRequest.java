package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateWallPostRequest(@NotBlank @Size(max = 5000) String text,
                                     @NotEmpty @Size(max = 10) List<MediaItemRequest> media) {
}
