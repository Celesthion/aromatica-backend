package com.aromatica.modules.inventory.doc.endpoint.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocListItem;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocRequest;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.EventDto;
import com.aromatica.modules.inventory.doc.endpoint.DocEndpoint;
import com.aromatica.modules.inventory.doc.mapper.DocMapper;
import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.search.DocSearchRequest;
import com.aromatica.modules.inventory.doc.service.DocEventService;
import com.aromatica.modules.inventory.doc.service.DocSearchService;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.doc.service.PostingService;
import com.aromatica.security.auth.authentication.CurrentUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocEndpointImpl implements DocEndpoint {

    DocService docService;
    DocSearchService searchService;
    PostingService postingService;
    DocEventService docEventService;
    DocMapper docMapper;
    PageMapper pageMapper;

    @Override
    @Transactional
    public DocDto create(DocRequest request) {
        requireAdminForCashierType(request.type());
        requireAdminForPayment(request.payAccountId());
        return docMapper.toDto(docService.create(docMapper.toEntity(request)));
    }

    @Override
    @Transactional
    public DocDto update(
            UUID id,
            DocRequest request
    ) {
        requireAdminForCashierType(request.type());
        requireAdminForPayment(request.payAccountId());
        requireAdminRights(docService.find(id));
        return docMapper.toDto(docService.update(id, docMapper.toEntity(request)));
    }

    /** Проведение и событие аудита — одна транзакция: событие не потеряется. */
    @Override
    @Transactional
    public DocDto post(UUID id) {
        // распроведённый админом чек проводит обратно тоже админ, иначе защита распроведения обходится
        requireAdminRights(docService.find(id));
        DocDto dto = docMapper.toDto(postingService.post(id));
        docEventService.record(id, DocEvent.Action.POST, CurrentUser.login());
        return dto;
    }

    @Override
    @Transactional
    public DocDto unpost(UUID id) {
        requireAdminRights(docService.find(id));
        DocDto dto = docMapper.toDto(postingService.unpost(id));
        docEventService.record(id, DocEvent.Action.UNPOST, CurrentUser.login());
        return dto;
    }

    @Override
    @Transactional
    public void cancel(UUID id) {
        requireAdminForCashierDoc(docService.find(id));
        docService.cancel(id);
        docEventService.record(id, DocEvent.Action.CANCEL, CurrentUser.login());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventDto> events(UUID id) {
        return docMapper.toEventDtos(docEventService.of(id));
    }

    @Override
    @Transactional(readOnly = true)
    public DocDto get(UUID id) {
        return docMapper.toDto(docService.find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocListItem> search(DocSearchRequest request) {
        Page<StockDoc> page = searchService.search(request);
        List<UUID> ids = page.getContent().stream().map(StockDoc::getId).toList();
        return pageMapper.toResponse(page, docMapper.toListItems(page.getContent(), docService.lineCounts(ids)));
    }

    /**
     * Кассовый чек и документ с оплатой со счёта трогает только администратор: иначе старший
     * продавец распровёл бы чужой чек в обход правила «только свой чек» из VoidReceiptUseCase.
     */
    private void requireAdminRights(StockDoc doc) {
        requireAdminForCashierDoc(doc);
        requireAdminForPayment(doc.getPayAccountId());
    }

    /** Такой документ пишет и снимает движения денег, а деньги — раздел администратора. */
    private static void requireAdminForPayment(UUID payAccountId) {
        if (payAccountId != null && !CurrentUser.isAdmin()) {
            throw new AccessDeniedException("Документ с оплатой со счёта оформляет только администратор");
        }
    }

    private void requireAdminForCashierDoc(StockDoc doc) {
        boolean cashierDoc = doc.getShiftId() != null
                && (doc.getDocType() == DocType.SALE || doc.getDocType() == DocType.RETURN);
        if (cashierDoc && !CurrentUser.isAdmin()) {
            throw new AccessDeniedException(
                    "Кассовый чек может распровести или отменить только администратор");
        }
    }

    private void requireAdminForCashierType(DocType type) {
        if ((type == DocType.SALE || type == DocType.RETURN) && !CurrentUser.isAdmin()) {
            throw new AccessDeniedException(
                    "Продажу и возврат оформляет касса, а не раздел документов");
        }
    }

}
