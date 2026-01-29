package org.nishgrid.clienterp.repository;

import org.nishgrid.clienterp.dto.HsnAggregationDTO;
import org.nishgrid.clienterp.model.SalesItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalesItemRepository extends JpaRepository<SalesItem, Long> {

    @Query("SELECT new org.nishgrid.clienterp.dto.HsnAggregationDTO(" +
            "s.hsnCode, " +
            "COALESCE(SUM(s.netWeight), 0.0), " +
            "CAST(COALESCE(SUM(s.totalPrice), 0.0) AS double), " +
            "CAST(COALESCE(SUM(s.totalPrice * s.invoice.gstPercent / 100.0), 0.0) AS double)) " +
            "FROM SalesItem s " +
            "WHERE YEAR(s.invoice.invoiceDate) = :year AND MONTH(s.invoice.invoiceDate) = :month " +
            "GROUP BY s.hsnCode")
    List<HsnAggregationDTO> getHsnSummaryForMonth(@Param("year") int year, @Param("month") int month);

    List<SalesItem> findByInvoice_InvoiceId(Long invoiceId);
}
