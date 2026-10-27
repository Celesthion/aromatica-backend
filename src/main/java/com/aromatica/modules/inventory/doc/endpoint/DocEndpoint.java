package com.aromatica.modules.inventory.doc.endpoint;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocListItem;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocRequest;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.EventDto;
import com.aromatica.modules.inventory.doc.search.DocSearchRequest;

import java.util.List;
import java.util.UUID;

/** Складские документы: DTO-слой (черновики, проведение, оплата, история). */
public interface DocEndpoint {

    DocDto create(DocRequest request);

    DocDto update(UUID id, DocRequest request);

    DocDto post(UUID id);

    DocDto unpost(UUID id);

    void cancel(UUID id);

    List<EventDto> events(UUID id);

    DocDto get(UUID id);

    PageResponse<DocListItem> search(DocSearchRequest request);
}
