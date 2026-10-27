package com.aromatica.modules.inventory.doc.endpoint;

import com.aromatica.common.search.PageMapper;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocRequest;
import com.aromatica.modules.inventory.doc.endpoint.impl.DocEndpointImpl;
import com.aromatica.modules.inventory.doc.mapper.DocMapper;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.service.DocEventService;
import com.aromatica.modules.inventory.doc.service.DocSearchService;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.doc.service.PostingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Кассовый чек (SALE/RETURN со сменой) правит и распроводит только ADMIN. */
@ExtendWith(MockitoExtension.class)
class DocEndpointSecurityTest {

    @Mock DocService docService;
    @Mock PostingService postingService;
    @Mock DocSearchService searchService;
    @Mock DocEventService docEventService;
    @Mock DocMapper docMapper;
    @Mock PageMapper pageMapper;

    private DocEndpointImpl endpoint() {
        return new DocEndpointImpl(docService, searchService, postingService, docEventService,
                docMapper, pageMapper);
    }

    private void authAs(String... roles) {
        var authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "x", authorities));
    }

    private StockDoc cashierSale() {
        StockDoc doc = new StockDoc();
        doc.setId(UUID.randomUUID());
        doc.setDocType(DocType.SALE);
        doc.setShiftId(UUID.randomUUID());
        return doc;
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void seniorSeller_cannotUnpostCashierReceipt() {
        UUID id = UUID.randomUUID();
        when(docService.find(id)).thenReturn(cashierSale());
        authAs("SENIOR_SELLER");

        assertThatThrownBy(() -> endpoint().unpost(id))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("администратор");
    }

    @Test
    void seniorSeller_cannotCancelCashierReceipt() {
        UUID id = UUID.randomUUID();
        when(docService.find(id)).thenReturn(cashierSale());
        authAs("SENIOR_SELLER");

        assertThatThrownBy(() -> endpoint().cancel(id))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Чек, который админ распровёл, лежит черновиком. Провести его обратно —
     * тоже только админу, иначе защита распроведения обходится в один шаг.
     */
    @Test
    void seniorSeller_cannotRepostCashierReceipt() {
        UUID id = UUID.randomUUID();
        when(docService.find(id)).thenReturn(cashierSale());
        authAs("SENIOR_SELLER");

        assertThatThrownBy(() -> endpoint().post(id))
                .isInstanceOf(AccessDeniedException.class);
    }

    private StockDoc paidSupply() {
        StockDoc doc = new StockDoc();
        doc.setId(UUID.randomUUID());
        doc.setDocType(DocType.SUPPLY);
        doc.setPayAccountId(UUID.randomUUID());
        return doc;
    }

    @Test
    void merchandiser_cannotCreateSupplyPaidFromAccount() {
        authAs("MERCHANDISER");
        DocRequest request = new DocRequest(DocType.SUPPLY, null, UUID.randomUUID(), null, null,
                null, null, null, UUID.randomUUID(), List.of());

        assertThatThrownBy(() -> endpoint().create(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("оплатой");
        verify(docService, never()).create(any());
    }

    @Test
    void merchandiser_cannotPostOrUnpostPaidDocument() {
        StockDoc doc = paidSupply();
        when(docService.find(doc.getId())).thenReturn(doc);
        authAs("MERCHANDISER");

        assertThatThrownBy(() -> endpoint().post(doc.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> endpoint().unpost(doc.getId())).isInstanceOf(AccessDeniedException.class);
        verify(postingService, never()).post(any());
        verify(postingService, never()).unpost(any());
    }

    @Test
    void merchandiser_canPostUnpaidSupply() {
        StockDoc doc = paidSupply();
        doc.setPayAccountId(null);
        when(docService.find(doc.getId())).thenReturn(doc);
        when(postingService.post(doc.getId())).thenReturn(doc);
        authAs("MERCHANDISER");

        assertThatCode(() -> endpoint().post(doc.getId())).doesNotThrowAnyException();
    }

    @Test
    void admin_canUnpostCashierReceipt() {
        UUID id = UUID.randomUUID();
        StockDoc doc = cashierSale();
        when(docService.find(id)).thenReturn(doc);
        when(postingService.unpost(id)).thenReturn(doc);
        lenient().when(docMapper.toDto(doc)).thenReturn((DocDto) null);
        authAs("ADMIN");

        assertThatCode(() -> endpoint().unpost(id)).doesNotThrowAnyException();
    }

    @Test
    void seniorSeller_canUnpostWarehouseDoc() {
        UUID id = UUID.randomUUID();
        StockDoc supply = new StockDoc();
        supply.setId(id);
        supply.setDocType(DocType.SUPPLY); // склад, не касса — распроведение разрешено
        when(docService.find(id)).thenReturn(supply);
        when(postingService.unpost(id)).thenReturn(supply);
        lenient().when(docMapper.toDto(supply)).thenReturn((DocDto) null);
        authAs("SENIOR_SELLER");

        assertThatCode(() -> endpoint().unpost(id)).doesNotThrowAnyException();
    }
}
