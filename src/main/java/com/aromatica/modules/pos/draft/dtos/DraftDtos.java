package com.aromatica.modules.pos.draft.dtos;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;

/** Деньги в минорных единицах (тыйын). */
public final class DraftDtos {

    private DraftDtos() {}

    /** payload — снапшот корзины как есть, бэк его не разбирает. */
    public record CreateDraftRequest(

            Long sumMinor,

            Integer positions,

            String preview,

            @NotNull(message = "Нет содержимого чека")
            JsonNode payload
    ) {
        public CreateDraftRequest {
            sumMinor = requireNonNullElse(sumMinor, 0L);
            positions = requireNonNullElse(positions, 0);
        }
    }

    public record DraftDto(
            UUID id,
            long sumMinor,
            int positions,
            String preview,
            JsonNode payload,
            LocalDateTime createdAt
    ) {}
}
