package com.aromatica.modules.pos.draft.mapper;

import com.aromatica.modules.pos.draft.dtos.DraftDtos.CreateDraftRequest;
import com.aromatica.modules.pos.draft.dtos.DraftDtos.DraftDto;
import com.aromatica.modules.pos.draft.model.PosDraft;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * payload в базе хранится строкой jsonb, наружу отдаём JSON-объектом.
 * Web-слой Spring Boot 4 на Jackson 3, поэтому tools.jackson.
 */
@Mapper
public abstract class DraftMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mapping(target = "payload", expression = "java(parsePayload(draft.getPayload()))")
    public abstract DraftDto toDto(PosDraft draft);

    /**
     * Только то, что прислала касса; id и смену ставит сервис.
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE, unmappedSourcePolicy = ReportingPolicy.ERROR)
    public abstract PosDraft toEntity(CreateDraftRequest request);

    protected String writePayload(JsonNode payload) {
        return payload == null ? null : payload.toString();
    }

    protected JsonNode parsePayload(String raw) {
        return raw == null ? null : JSON.readTree(raw);
    }
}
