package com.aromatica.modules.pos.draft.service;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.NotFoundException;
import com.aromatica.modules.pos.draft.dtos.DraftDtos.CreateDraftRequest;
import com.aromatica.modules.pos.draft.dtos.DraftDtos.DraftDto;
import com.aromatica.modules.pos.draft.mapper.DraftMapper;
import com.aromatica.modules.pos.draft.mapper.DraftMapperImpl;
import com.aromatica.modules.pos.draft.model.PosDraft;
import com.aromatica.modules.pos.draft.repository.PosDraftRepository;
import com.aromatica.modules.pos.draft.service.impl.PosDraftServiceImpl;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Отложенные чеки кассы (черновики корзины). Данные синтетические, не показатели магазина. */
@ExtendWith(MockitoExtension.class)
class PosDraftServiceTest {

    private static final UUID WAREHOUSE = UUID.randomUUID();

    @Mock
    private PosDraftRepository draftRepository;
    @Mock
    private PosShiftService shiftService;

    private PosDraftService service;
    private final DraftMapper mapper = new DraftMapperImpl();

    private PosShift shift;

    @BeforeEach
    void setUp() {
        service = new PosDraftServiceImpl(draftRepository, shiftService);
        shift = new PosShift();
        shift.setId(UUID.randomUUID());
        shift.setWarehouseId(WAREHOUSE);
        shift.setStatus(PosShift.Status.OPEN);
        // по умолчанию смена доступна и открыта, тесты на отказ настраивают своё
        lenient().when(shiftService.requireAccessibleOpenShift(shift.getId(), "seller"))
                .thenReturn(shift);
        lenient().when(draftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static JsonNode payload() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.putArray("items").addObject().put("productId", "test-aroma-1").put("qty", 1);
        return node;
    }

    private static CreateDraftRequest request() {
        return new CreateDraftRequest(250_000L, 2, "Test Aroma, Test Box", payload());
    }

    @Test
    void create_savesSnapshotWithDenormalizedFields() {
        when(draftRepository.countByShiftId(shift.getId())).thenReturn(3L);

        DraftDto dto = mapper.toDto(service.create(shift.getId(), mapper.toEntity(request()), "seller"));

        ArgumentCaptor<PosDraft> captor = ArgumentCaptor.forClass(PosDraft.class);
        verify(draftRepository).save(captor.capture());
        PosDraft saved = captor.getValue();
        assertThat(saved.getShiftId()).isEqualTo(shift.getId());
        assertThat(saved.getSumMinor()).isEqualTo(250_000L);
        assertThat(saved.getPositions()).isEqualTo(2);
        assertThat(saved.getPreview()).isEqualTo("Test Aroma, Test Box");
        // payload — непрозрачный снапшот, хранится и отдаётся как есть
        assertThat(saved.getPayload()).isEqualTo(payload().toString());
        assertThat(dto.payload()).isEqualTo(payload());
    }

    @Test
    void create_rejectsEleventhDraft() {
        // лимит 10 на смену, на одиннадцатый черновик ответ 400
        when(draftRepository.countByShiftId(shift.getId())).thenReturn(10L);

        assertThatThrownBy(() -> service.create(shift.getId(), mapper.toEntity(request()), "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("10");
        verify(draftRepository, never()).save(any());
    }

    @Test
    void create_rejectsMissingPayload() {
        assertThatThrownBy(() -> service.create(shift.getId(),
                mapper.toEntity(new CreateDraftRequest(0L, 0, null, null)), "seller"))
                .isInstanceOf(BusinessRuleException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void create_rejectsSellerWithoutShiftAccess() {
        // доступ проверяет PosShiftService.requireAccessibleOpenShift, как и для чеков
        when(shiftService.requireAccessibleOpenShift(shift.getId(), "stranger"))
                .thenThrow(new AccessDeniedException("Смена другого склада"));

        assertThatThrownBy(() -> service.create(shift.getId(), mapper.toEntity(request()), "stranger"))
                .isInstanceOf(AccessDeniedException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void list_checksAccessAndReturnsNewestFirst() {
        PosDraft draft = new PosDraft();
        draft.setId(UUID.randomUUID());
        draft.setShiftId(shift.getId());
        draft.setPayload(payload().toString());
        when(draftRepository.findByShiftIdOrderByCreatedAtDesc(shift.getId()))
                .thenReturn(List.of(draft));

        List<PosDraft> result = service.list(shift.getId(), "seller");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(draft.getId());
        verify(shiftService).requireAccessibleOpenShift(shift.getId(), "seller");
    }

    @Test
    void delete_checksAccessThroughDraftShift() {
        PosDraft draft = new PosDraft();
        draft.setId(UUID.randomUUID());
        draft.setShiftId(shift.getId());
        when(draftRepository.require(eq(draft.getId()), anyString())).thenReturn(draft);

        service.delete(draft.getId(), "seller");

        verify(shiftService).requireAccessibleOpenShift(shift.getId(), "seller");
        verify(draftRepository).delete(draft);
    }

    @Test
    void delete_missingDraftIs404() {
        UUID id = UUID.randomUUID();
        when(draftRepository.require(eq(id), anyString())).thenThrow(new NotFoundException("Черновик не найден: " + id));

        assertThatThrownBy(() -> service.delete(id, "seller"))
                .isInstanceOf(NotFoundException.class);
        verify(draftRepository, never()).delete(any());
    }
}
