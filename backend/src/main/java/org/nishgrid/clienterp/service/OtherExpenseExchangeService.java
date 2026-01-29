package org.nishgrid.clienterp.service;

import org.nishgrid.clienterp.dto.OtherExpenseExchangeRequest;
import org.nishgrid.clienterp.dto.OtherExpenseExchangeResponse;
import java.util.List;

public interface OtherExpenseExchangeService {
    OtherExpenseExchangeResponse createExchange(OtherExpenseExchangeRequest request);
    List<OtherExpenseExchangeResponse> getAllExchanges();
    void deleteExchange(Long id);
}