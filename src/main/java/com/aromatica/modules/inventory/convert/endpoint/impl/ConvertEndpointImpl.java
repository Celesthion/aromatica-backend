package com.aromatica.modules.inventory.convert.endpoint.impl;

import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.ConvertRequest;
import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.SplitInfo;
import com.aromatica.modules.inventory.convert.endpoint.ConvertEndpoint;
import com.aromatica.modules.inventory.convert.mapper.ConvertMapper;
import com.aromatica.modules.inventory.convert.service.ConvertService;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.inventory.doc.mapper.DocMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConvertEndpointImpl implements ConvertEndpoint {

    ConvertService convertService;
    ConvertMapper convertMapper;
    DocMapper docMapper;

    /** Транзакция на весь метод: toDto читает ленивые строки документа, а open-in-view выключен. */
    @Override
    @Transactional
    public DocDto open(ConvertRequest request) {
        return docMapper.toDto(convertService.open(convertMapper.toCommand(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public SplitInfo splitInfo(UUID sourceProductId) {
        return convertMapper.toSplitInfo(convertService.splitInfo(sourceProductId));
    }
}
