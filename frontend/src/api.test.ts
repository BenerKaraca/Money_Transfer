import { afterEach, expect, it, vi } from 'vitest'
import { api, authToken } from './api'

afterEach(() => vi.unstubAllGlobals())

it('sends the account and idempotent transfer/payment requests', async () => {
  const fetchMock = vi.fn(async (url: string, _options?: RequestInit) => ({ ok: true, json: async () => ({ id: url, status: 'PENDING' }) }))
  vi.stubGlobal('fetch', fetchMock)
  authToken.set('jwt-token')

  await api.login('bener', 'secret')
  await api.account('account-1')
  await api.accounts()
  await api.payees()
  await api.ledger('account-1')
  await api.transfer('source', 'destination', 12.5, 'transfer-key')
  await api.billPayment('source', 8.25, 'APPROVE', 'payment-key')
  await api.paymentStatus('payment-1')
  await api.payments('account-1')
  await api.rates()
  await api.quote('TRY', 'EUR', 100)
  await api.trade('account-1', 'account-2', 'quote-1', 'fx-key')
  await api.trades('account-1')
  await api.spending()

  expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/login')
  expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/accounts/account-1')
  expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/accounts?size=100')
  expect(fetchMock.mock.calls[3]?.[0]).toBe('/api/payees?currency=TRY')
  expect(fetchMock.mock.calls[4]?.[0]).toBe('/api/accounts/account-1/ledger')
  expect(fetchMock.mock.calls[5]?.[0]).toBe('/api/transfers')
  expect(fetchMock.mock.calls[5]?.[1]).toMatchObject({
    method: 'POST',
    body: JSON.stringify({ sourceAccountId: 'source', destinationAccountId: 'destination', amount: 12.5 }),
  })
  expect(new Headers(fetchMock.mock.calls[5]?.[1]?.headers).get('Idempotency-Key')).toBe('transfer-key')
  expect(new Headers(fetchMock.mock.calls[5]?.[1]?.headers).get('Authorization')).toBe('Bearer jwt-token')
  expect(fetchMock.mock.calls[6]?.[1]).toMatchObject({
    method: 'POST',
    body: JSON.stringify({ accountId: 'source', amount: 8.25, scenario: 'APPROVE' }),
  })
  expect(fetchMock.mock.calls[7]?.[0]).toBe('/api/bill-payments/payment-1')
  expect(fetchMock.mock.calls[8]?.[0]).toBe('/api/bill-payments?accountId=account-1')
  expect(fetchMock.mock.calls[9]?.[0]).toBe('/api/fx/rates')
  expect(fetchMock.mock.calls[10]?.[1]).toMatchObject({ body: JSON.stringify({ sourceCurrency: 'TRY', targetCurrency: 'EUR', amount: 100 }) })
  expect(fetchMock.mock.calls[11]?.[1]).toMatchObject({
    body: JSON.stringify({ sourceAccountId: 'account-1', destinationAccountId: 'account-2', quoteId: 'quote-1' }) })
  expect(new Headers(fetchMock.mock.calls[11]?.[1]?.headers).get('Idempotency-Key')).toBe('fx-key')
  expect(fetchMock.mock.calls[12]?.[0]).toBe('/api/exchanges?accountId=account-1')
  expect(fetchMock.mock.calls[13]?.[0]).toBe('/api/spending')
  authToken.clear()
  expect(authToken.get()).toBeNull()
})

it('uses server error messages and falls back to HTTP status', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 409, json: async () => ({ message: 'Conflict' }) })))
  await expect(api.account('missing')).rejects.toThrow('Conflict')
  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 503, json: async () => ({}) })))
  await expect(api.account('missing')).rejects.toThrow('HTTP 503')
})
