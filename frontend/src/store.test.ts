import { afterEach, expect, it, vi } from 'vitest'
import { clearError, clearQuote, executeTrade, loadAccount, loadAccounts, loadLedger, loadPayees, loadPayments, loadRates, loadSpending, loadTrades, refreshPayment, requestQuote, sendTransfer, startPayment, store } from './store'

afterEach(() => vi.unstubAllGlobals())

it('tracks pending, fulfilled, rejected and fallback error states', async () => {
  let release!: (value: unknown) => void
  const pending = new Promise(resolve => { release = resolve })
  vi.stubGlobal('fetch', vi.fn(() => pending))
  const loading = store.dispatch(loadAccount('account-1'))
  expect(store.getState().bank.busy).toBe(false)
  release({ ok: true, json: async () => ({ id: 'account-1', balance: 100, availableBalance: 100 }) })
  await loading
  expect(store.getState().bank.accounts['account-1']?.balance).toBe(100)

  let releaseTransfer!: (value: unknown) => void
  const waitingTransfer = new Promise(resolve => { releaseTransfer = resolve })
  vi.stubGlobal('fetch', vi.fn(() => waitingTransfer))
  const sending = store.dispatch(sendTransfer({ source: 'account-1', destination: 'account-2', amount: 10, key: 'waiting' }))
  expect(store.getState().bank.busy).toBe(true)
  releaseTransfer({ ok: true, json: async () => ({ id: 'transfer-waiting', status: 'COMPLETED' }) })
  await sending
  expect(store.getState().bank.busy).toBe(false)

  vi.stubGlobal('fetch', vi.fn(async (url: string) => ({
    ok: true,
    json: async () => url === '/api/transfers'
      ? { id: 'transfer-1', status: 'COMPLETED' }
      : { id: 'payment-1', status: 'PENDING' },
  })))
  await store.dispatch(sendTransfer({ source: 'account-1', destination: 'account-2', amount: 10, key: 't' }))
  expect(store.getState().bank.lastTransfer?.id).toBe('transfer-1')
  await store.dispatch(startPayment({ accountId: 'account-1', amount: 10, scenario: 'APPROVE', key: 'p' }))
  expect(store.getState().bank.payment?.status).toBe('PENDING')
  await store.dispatch(refreshPayment('payment-1'))
  expect(store.getState().bank.payment?.id).toBe('payment-1')

  vi.stubGlobal('fetch', vi.fn(async (url: string) => ({ ok: true, json: async () => {
    if (url.startsWith('/api/accounts?')) return { content: [
      { id: 'a', customerId: 'owner', balance: 12 },
      { id: 'clearing', customerId: 'ffffffff-ffff-ffff-ffff-fffffffffff0', balance: 99 },
    ] }
    if (url.endsWith('/ledger')) return [{ id: 'entry' }]
    if (url.startsWith('/api/payees')) return [{ id: 'payee' }]
    if (url === '/api/spending') return { items: [{ id: 'spend' }], totalsByCurrency: { TRY: 10 } }
    if (url.startsWith('/api/bill-payments?')) return [{ id: 'payment' }]
    if (url === '/api/fx/rates') return [{ base: 'EUR', quote: 'TRY', rate: 50 }]
    if (url.startsWith('/api/exchanges?')) return [{ id: 'trade' }]
    if (url === '/api/fx/quotes') return { id: 'quote' }
    return { id: 'trade-new' }
  } })))
  await store.dispatch(loadAccounts())
  await store.dispatch(loadLedger('a'))
  await store.dispatch(loadPayees())
  await store.dispatch(loadSpending())
  await store.dispatch(loadPayments('a'))
  await store.dispatch(loadRates())
  await store.dispatch(loadTrades('a'))
  await store.dispatch(requestQuote({ from: 'TRY', to: 'EUR', amount: 10 }))
  expect(store.getState().bank.accountList).toHaveLength(2)
  expect(store.getState().bank.accounts.a?.balance).toBe(12)
  expect(store.getState().bank.ledger).toHaveLength(1)
  expect(store.getState().bank.payees).toHaveLength(1)
  expect(store.getState().bank.spending.items).toHaveLength(1)
  expect(store.getState().bank.payments).toHaveLength(1)
  expect(store.getState().bank.rates).toHaveLength(1)
  expect(store.getState().bank.trades).toHaveLength(1)
  expect(store.getState().bank.quote?.id).toBe('quote')
  store.dispatch(clearQuote())
  expect(store.getState().bank.quote).toBeNull()
  await store.dispatch(executeTrade({ source: 'a', destination: 'b', quoteId: 'quote', key: 'fx' }))
  expect(store.getState().bank.lastTrade?.id).toBe('trade-new')

  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 409, json: async () => ({ message: 'Insufficient' }) })))
  await store.dispatch(sendTransfer({ source: 'account-1', destination: 'account-2', amount: 200, key: 'failed' }))
  expect(store.getState().bank.error).toBe('Insufficient')
  expect(store.getState().bank.busy).toBe(false)
  store.dispatch(clearError())
  expect(store.getState().bank.error).toBeNull()
  store.dispatch({ type: sendTransfer.rejected.type, error: {}, meta: { requestId: 'missing-message', requestStatus: 'rejected' } })
  expect(store.getState().bank.error).toBe('İstek başarısız')
})
