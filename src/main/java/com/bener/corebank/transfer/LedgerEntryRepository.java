package com.bener.corebank.transfer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    @Query("select l from LedgerEntry l where l.account.id = :accountId order by l.createdAt desc")
    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(@Param("accountId") UUID accountId);

    @Query("select l from LedgerEntry l where l.transfer.id = :transferId")
    List<LedgerEntry> findByTransferId(@Param("transferId") UUID transferId);
}
