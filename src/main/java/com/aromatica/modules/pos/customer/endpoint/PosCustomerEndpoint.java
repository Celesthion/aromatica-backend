package com.aromatica.modules.pos.customer.endpoint;

import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PosCustomerDto;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PosCustomerRequest;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PurchaseDto;

import java.util.List;
import java.util.UUID;

public interface PosCustomerEndpoint {

    List<PosCustomerDto> search(String query);

    PosCustomerDto create(PosCustomerRequest request);

    List<PurchaseDto> purchases(UUID customerId);
}
