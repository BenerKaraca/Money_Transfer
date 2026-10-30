export type Account = {
  id: string
  customerId: string
  customerName?: string
  currency: string
  balance: number
  availableBalance: number
}

export type Transfer = {
  id: string
  sourceAccountId: string
  destinationAccountId: string
  amount: number
  status: string
}

export type BillPayment = {
  id: string
  accountId: string
  amount: number
  status: 'PENDING' | 'UNKNOWN' | 'SUCCEEDED' | 'FAILED'
}

export type LedgerEntry = { id: string; transferId: string; direction: 'DEBIT' | 'CREDIT'; amount: number; balanceAfter: number; createdAt: string }
export type FxRate = { date: string; base: string; quote: string; rate: number }
export type FxQuote = { id: string; sourceCurrency: string; targetCurrency: string; sourceAmount: number; targetAmount: number; rate: number; rateDate: string; expiresAt: string }
export type FxTrade = { id: string; quoteId: string; sourceAccountId: string; destinationAccountId: string; sourceAmount: number; targetAmount: number; rate: number; rateDate: string; createdAt: string }
export type LoginResult = { accessToken: string; expiresAt: string; username: string; customerId: string; role: string }
export type SpendingItem = { id: string; type: 'TRANSFER' | 'BILL_PAYMENT' | 'FX_EXCHANGE'; description: string; currency: string; amount: number; occurredAt: string }
export type SpendingHistory = { items: SpendingItem[]; totalsByCurrency: Record<string, number> }

const tokenKey = 'corebank_access_token'
export const authToken = {
  get: () => sessionStorage.getItem(tokenKey),
  set: (token: string) => sessionStorage.setItem(tokenKey, token),
  clear: () => sessionStorage.removeItem(tokenKey),
}

async function json<T>(url: string, options?: RequestInit): Promise<T> {
  const token = authToken.get()
  const headers = new Headers(options?.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(url, { ...options, headers })
  const body = await response.json()
  if (!response.ok) throw new Error(body.message ?? `HTTP ${response.status}`)
  return body as T
}

export const api = {
  login: (username: string, password: string) => json<LoginResult>('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }),
  }),
  account: (id: string) => json<Account>(`/api/accounts/${id}`),
  accounts: () => json<{ content: Account[] }>('/api/accounts?size=100'),
  payees: () => json<Account[]>('/api/payees?currency=TRY'),
  ledger: (id: string) => json<LedgerEntry[]>(`/api/accounts/${id}/ledger`),
  transfer: (sourceAccountId: string, destinationAccountId: string, amount: number, key: string) =>
    json<Transfer>('/api/transfers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
      body: JSON.stringify({ sourceAccountId, destinationAccountId, amount }),
    }),
  billPayment: (accountId: string, amount: number, scenario: string, key: string) =>
    json<BillPayment>('/api/bill-payments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
      body: JSON.stringify({ accountId, amount, scenario }),
    }),
  paymentStatus: (id: string) => json<BillPayment>(`/api/bill-payments/${id}`),
  payments: (accountId: string) => json<BillPayment[]>(`/api/bill-payments?accountId=${accountId}`),
  rates: () => json<FxRate[]>('/api/fx/rates'),
  quote: (sourceCurrency: string, targetCurrency: string, amount: number) =>
    json<FxQuote>('/api/fx/quotes', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sourceCurrency, targetCurrency, amount }) }),
  trade: (sourceAccountId: string, destinationAccountId: string, quoteId: string, key: string) =>
    json<FxTrade>('/api/exchanges', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
      body: JSON.stringify({ sourceAccountId, destinationAccountId, quoteId }) }),
  trades: (accountId: string) => json<FxTrade[]>(`/api/exchanges?accountId=${accountId}`),
  spending: () => json<SpendingHistory>('/api/spending'),
}
