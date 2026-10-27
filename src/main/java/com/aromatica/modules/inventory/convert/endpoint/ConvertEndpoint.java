package com.aromatica.modules.inventory.convert.endpoint;

import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.ConvertRequest;
import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.SplitInfo;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;

import java.util.UUID;

/** Вскрытие флакона/тестера в распив: DTO-слой. */
public interface ConvertEndpoint {

    DocDto open(ConvertRequest request);

    SplitInfo splitInfo(UUID sourceProductId);
}
