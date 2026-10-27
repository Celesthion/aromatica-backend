package com.aromatica.modules.finance.gift.endpoint;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.BlockRequest;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.CertificateCheck;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GenerateBatchRequest;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftCertificateDto;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftStats;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.UseDto;
import com.aromatica.modules.finance.gift.search.GiftCertificateSearchRequest;

import java.util.List;
import java.util.UUID;

public interface GiftCertificateEndpoint {

    PageResponse<GiftCertificateDto> search(GiftCertificateSearchRequest request);

    GiftStats stats();

    List<GiftCertificateDto> generateBatch(GenerateBatchRequest request);

    GiftCertificateDto block(UUID id, BlockRequest request);

    List<UseDto> history(UUID id);

    CertificateCheck check(String code);
}
