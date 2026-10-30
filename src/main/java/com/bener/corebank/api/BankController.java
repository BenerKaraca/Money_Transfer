package com.bener.corebank.api;

import com.bener.corebank.account.Account;
import com.bener.corebank.account.AccountService;
import com.bener.corebank.customer.Customer;
import com.bener.corebank.transfer.LedgerEntry;
import com.bener.corebank.transfer.LedgerEntryRepository;
import com.bener.corebank.transfer.Transfer;
import com.bener.corebank.transfer.TransferService;
import com.bener.corebank.security.BankAuthorization;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BankController {
    private final AccountService accountService;
    private final TransferService transferService;
    private final LedgerEntryRepository ledger;
    private final BankAuthorization authorization;

    public BankController(AccountService accountService, TransferService transferService, LedgerEntryRepository ledger,
                          BankAuthorization authorization) {
        this.accountService = accountService;
        this.transferService = transferService;
        this.ledger = ledger;
        this.authorization = authorization;
    }

    @PostMapping("/customers")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomer request) {
        Customer customer = accountService.createCustomer(request.fullName());
        return ResponseEntity.status(201).body(new CustomerResponse(customer.getId(), customer.getFullName()));
    }

    @PostMapping("/customers/{customerId}/accounts")
    public ResponseEntity<AccountResponse> openAccount(@PathVariable UUID customerId,
                                                        @RequestParam(defaultValue = "TRY") String currency) {
        authorization.requireOwnCustomer(customerId);
        return ResponseEntity.status(201).body(account(accountService.openAccount(customerId, currency)));
    }

    @GetMapping("/accounts/{accountId}")
    public AccountResponse getAccount(@PathVariable UUID accountId) {
        authorization.requireOwnAccount(accountId);
        return account(accountService.getAccount(accountId));
    }

    @GetMapping("/accounts")
    public Page<AccountService.AccountOverview> listAccounts(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return accountService.listAccounts(authorization.customerId(), page, size);
    }

    @GetMapping("/payees")
    public List<AccountService.AccountOverview> payees(@RequestParam(defaultValue = "TRY") String currency) {
        return accountService.payees(authorization.customerId(), currency);
    }

    @GetMapping("/accounts/{accountId}/ledger")
    public List<LedgerResponse> ledger(@PathVariable UUID accountId) {
        authorization.requireOwnAccount(accountId);
        accountService.getAccount(accountId);
        return ledger.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(entry -> new LedgerResponse(entry.getId(), entry.getTransferId(), entry.getDirection(),
                        entry.getAmount(), entry.getBalanceAfter(), entry.getCreatedAt()))
                .toList();
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
                                                     @Valid @RequestBody TransferRequest request) {
        authorization.requireOwnAccount(request.sourceAccountId());
        Transfer transfer = transferService.transfer(request.sourceAccountId(), request.destinationAccountId(), request.amount(), key);
        return ResponseEntity.status(201).body(response(transfer));
    }

    @GetMapping("/transfers/{id}")
    public TransferResponse getTransfer(@PathVariable UUID id) {
        Transfer transfer = transferService.getTransfer(id);
        authorization.requireOwnAccount(transfer.getSourceAccountId());
        return response(transfer);
    }

    private static AccountResponse account(Account account) {
        return new AccountResponse(account.getId(), account.getCustomerId(), account.getCustomerName(), account.getCurrency(),
                account.getBalance(), account.getAvailableBalance());
    }

    private static TransferResponse response(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getSourceAccountId(), transfer.getDestinationAccountId(),
                transfer.getAmount(), transfer.getStatus(), transfer.getCreatedAt());
    }

    public record CreateCustomer(@NotBlank @Size(max = 120) String fullName) {}
    public record CustomerResponse(UUID id, String fullName) {}
    public record AccountResponse(UUID id, UUID customerId, String customerName, String currency, BigDecimal balance,
                                  BigDecimal availableBalance) {}
    public record TransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId,
                                  @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}
    public record TransferResponse(UUID id, UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount,
                                   String status, Instant createdAt) {}
    public record LedgerResponse(UUID id, UUID transferId, String direction, BigDecimal amount,
                                 BigDecimal balanceAfter, Instant createdAt) {}
}
