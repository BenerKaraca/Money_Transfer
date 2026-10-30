import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'

const senderId = '20000000-0000-0000-0000-000000000001'
const receiverId = '20000000-0000-0000-0000-000000000002'
const thirdId = '20000000-0000-0000-0000-000000000003'
const eurId = '20000000-0000-0000-0000-000000000004'
const usdId = '20000000-0000-0000-0000-000000000005'
const ownerId = '10000000-0000-0000-0000-000000000001'
const accounts = [
  { id: senderId, customerId: ownerId, customerName: 'Demo Sender', currency: 'TRY', balance: 5000, availableBalance: 5000 },
  { id: receiverId, customerId: 'receiver', customerName: 'Mehmet Kaya', currency: 'TRY', balance: 0, availableBalance: 0 },
  { id: thirdId, customerId: 'third', currency: 'TRY', balance: 0, availableBalance: 0 },
  { id: eurId, customerId: ownerId, customerName: 'Demo Sender', currency: 'EUR', balance: 10, availableBalance: 10 },
  { id: usdId, customerId: ownerId, customerName: 'Demo Sender', currency: 'USD', balance: 0, availableBalance: 0 },
  { id: 'clearing', customerId: 'ffffffff-ffff-ffff-ffff-fffffffffff0', customerName: 'Clearing', currency: 'TRY', balance: 0, availableBalance: 0 },
]
const ledger = [
  { id: 'entry-1', transferId: 'transfer-123', direction: 'DEBIT', amount: 20, balanceAfter: 4980, createdAt: '2026-10-28T10:00:00Z' },
  { id: 'entry-2', transferId: 'transfer-456', direction: 'CREDIT', amount: 10, balanceAfter: 4990, createdAt: '2026-10-28T09:00:00Z' },
]
const rates = [
  { date: '2026-10-27', base: 'EUR', quote: 'TRY', rate: 55.639 },
  { date: '2026-10-27', base: 'USD', quote: 'TRY', rate: 47.2 },
  { date: '2026-10-27', base: 'EUR', quote: 'USD', rate: 1.17 },
]
const payments = ['PENDING', 'UNKNOWN', 'SUCCEEDED', 'FAILED'].map((status, index) => ({ id: `payment-${index}`, accountId: senderId, amount: 10, status }))
const trades = [{ id: 'trade-123', quoteId: 'quote-old', sourceAccountId: senderId, destinationAccountId: eurId, sourceAmount: 100, targetAmount: 2, rate: 0.02, rateDate: '2026-10-27', createdAt: '2026-10-28T10:00:00Z' }]

it('shows a usable dashboard and completes transfers, payments and FX trades', async () => {
  document.body.innerHTML = '<div id="root"></div>'
  sessionStorage.setItem('corebank_access_token', 'test-jwt')
  let sequence = 0
  vi.stubGlobal('crypto', { randomUUID: () => `key-${++sequence}` })
  let transferFails = false
  let paymentFails = false
  let quoteFails = false
  let tradeFails = false
  let statusPolls = 0
  const response = (body: unknown, ok = true, status = 200) => ({ ok, status, json: async () => body })
  const fetchMock = vi.fn(async (url: string, _options?: RequestInit) => {
    if (url === '/api/accounts?size=100') return response({ content: accounts.filter(account => account.customerId === ownerId) })
    if (url === '/api/payees?currency=TRY') return response(accounts.filter(account => ['receiver', 'third'].includes(account.customerId)))
    if (url === '/api/spending') return response({ items: [
      { id: 'spend-1', type: 'TRANSFER', description: 'Transfer · Ayşe Demir', currency: 'TRY', amount: 20, occurredAt: '2026-10-28T10:00:00Z' },
      { id: 'spend-2', type: 'BILL_PAYMENT', description: 'Fatura ödemesi', currency: 'TRY', amount: 10, occurredAt: '2026-10-28T09:00:00Z' },
      { id: 'spend-3', type: 'FX_EXCHANGE', description: 'Döviz alımı · EUR', currency: 'TRY', amount: 100, occurredAt: '2026-10-28T08:00:00Z' },
    ], totalsByCurrency: { TRY: 130 } })
    if (url.startsWith('/api/accounts/') && url.endsWith('/ledger')) return response(ledger)
    if (url.startsWith('/api/accounts/')) return response(accounts.find(account => account.id === url.split('/').at(-1)))
    if (url === `/api/bill-payments?accountId=${senderId}`) return response(payments)
    if (url === `/api/exchanges?accountId=${senderId}`) return response(trades)
    if (url === '/api/fx/rates') return response(rates)
    if (url === '/api/transfers') return transferFails ? response({ message: 'Yetersiz bakiye' }, false, 409)
      : response({ id: 'transfer-new', status: 'COMPLETED' })
    if (url === '/api/bill-payments') return paymentFails ? response({ message: 'Sağlayıcı erişilemiyor' }, false, 503)
      : response({ id: 'payment-new', accountId: senderId, amount: 10, status: 'PENDING' })
    if (url === '/api/bill-payments/payment-new') {
      statusPolls += 1
      return response({ id: 'payment-new', accountId: senderId, amount: 10, status: statusPolls === 1 ? 'UNKNOWN' : 'SUCCEEDED' })
    }
    if (url === '/api/fx/quotes') return quoteFails ? response({ message: 'Kur alınamadı' }, false, 502)
      : response({ id: 'quote-1', sourceCurrency: 'TRY', targetCurrency: 'EUR', sourceAmount: 500, targetAmount: 10,
        rate: 0.02, rateDate: '2026-10-27', expiresAt: '2099-01-01T00:00:00Z' })
    if (url === '/api/exchanges') return tradeFails ? response({ message: 'İşlem reddedildi' }, false, 409) : response({ id: 'trade-new', quoteId: 'quote-1', sourceAccountId: senderId,
      destinationAccountId: eurId, sourceAmount: 500, targetAmount: 10, rate: 0.02, rateDate: '2026-10-27', createdAt: '2026-10-28T10:00:00Z' })
    throw new Error(`Unexpected request ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  await act(async () => { await import('./main') })
  await screen.findByText('Finansal görünümünüz')
  expect((await screen.findAllByText(/5\.000,00/)).length).toBeGreaterThan(0)
  expect(screen.getByText('Para birimi bakiyeleri')).toBeTruthy()
  expect(screen.getAllByText('EUR/TRY').length).toBeGreaterThan(0)
  fireEvent.click(screen.getByRole('button', { name: 'Harcama geçmişi →' }))
  expect(screen.getAllByText('Harcama geçmişi').length).toBeGreaterThan(1)
  fireEvent.click(screen.getByRole('button', { name: /Genel bakış/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Tümü →' }))
  fireEvent.click(screen.getByRole('button', { name: /Genel bakış/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Döviz al/sat →' }))
  fireEvent.click(screen.getByRole('button', { name: /Genel bakış/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Döviz işlemi →' }))
  fireEvent.click(screen.getByRole('button', { name: /Genel bakış/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Para gönder ↗' }))
  fireEvent.change(screen.getByLabelText('Gönderen hesap'), { target: { value: senderId } })
  const recipient = screen.getByLabelText('Alıcı hesap') as HTMLSelectElement
  expect(recipient.options).toHaveLength(3)
  fireEvent.change(recipient, { target: { value: thirdId } })
  fireEvent.change(screen.getByLabelText('Tutar (TRY)'), { target: { value: '0' } })
  expect(screen.getByRole('button', { name: 'Transfer yap ↗' }).hasAttribute('disabled')).toBe(true)
  fireEvent.change(screen.getByLabelText('Tutar (TRY)'), { target: { value: '20' } })
  fireEvent.click(screen.getByRole('button', { name: 'Transfer yap ↗' }))
  await screen.findByText(/Transfer tamamlandı/)
  transferFails = true
  fireEvent.click(screen.getByRole('button', { name: 'Transfer yap ↗' }))
  await screen.findByRole('alert')
  const failedTransfer = fetchMock.mock.calls.filter(call => call[0] === '/api/transfers').at(-1)![1]
  fireEvent.click(screen.getByRole('button', { name: 'Transfer yap ↗' }))
  await waitFor(() => expect(fetchMock.mock.calls.filter(call => call[0] === '/api/transfers')).toHaveLength(3))
  fireEvent.click(screen.getByRole('button', { name: 'Hatayı kapat' }))
  const retriedTransfer = fetchMock.mock.calls.filter(call => call[0] === '/api/transfers').at(-1)![1]
  expect((retriedTransfer?.headers as Record<string, string>)['Idempotency-Key'])
    .toBe((failedTransfer?.headers as Record<string, string>)['Idempotency-Key'])

  fireEvent.click(screen.getByRole('button', { name: /Ödemeler/ }))
  fireEvent.change(screen.getByLabelText('Ödeme hesabı'), { target: { value: senderId } })
  fireEvent.change(screen.getByLabelText('Tutar (TRY)'), { target: { value: '0' } })
  expect(screen.getByRole('button', { name: 'Ödemeyi başlat →' }).hasAttribute('disabled')).toBe(true)
  fireEvent.change(screen.getByLabelText('Tutar (TRY)'), { target: { value: '10' } })
  fireEvent.click(screen.getByText('İşlem sonucu senaryosu'))
  fireEvent.change(screen.getByLabelText('Sağlayıcı davranışı'), { target: { value: 'TIMEOUT_ONCE' } })
  fireEvent.click(screen.getByRole('button', { name: 'Ödemeyi başlat →' }))
  await screen.findByText(/Son ödeme: PENDING/)
  await waitFor(() => expect(statusPolls).toBe(2), { timeout: 7000 })
  await screen.findByText(/Son ödeme: SUCCEEDED/)
  paymentFails = true
  fireEvent.click(screen.getByRole('button', { name: 'Ödemeyi başlat →' }))
  await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Sağlayıcı erişilemiyor'))
  const failedPayment = fetchMock.mock.calls.filter(call => call[0] === '/api/bill-payments').at(-1)![1]
  fireEvent.click(screen.getByRole('button', { name: 'Ödemeyi başlat →' }))
  await waitFor(() => expect(fetchMock.mock.calls.filter(call => call[0] === '/api/bill-payments')).toHaveLength(3))
  const retriedPayment = fetchMock.mock.calls.filter(call => call[0] === '/api/bill-payments').at(-1)![1]
  expect((retriedPayment?.headers as Record<string, string>)['Idempotency-Key'])
    .toBe((failedPayment?.headers as Record<string, string>)['Idempotency-Key'])

  fireEvent.click(screen.getByRole('button', { name: /Döviz işlemleri/ }))
  expect(screen.getByText('Döviz alım satımı')).toBeTruthy()
  fireEvent.change(screen.getByLabelText('Satılacak tutar (TRY)'), { target: { value: '0' } })
  expect(screen.getByRole('button', { name: 'Kur teklifi al' }).hasAttribute('disabled')).toBe(true)
  fireEvent.change(screen.getByLabelText('Satılacak tutar (TRY)'), { target: { value: '500' } })
  quoteFails = true
  fireEvent.click(screen.getByRole('button', { name: 'Kur teklifi al' }))
  await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('Kur alınamadı'))
  quoteFails = false
  fireEvent.click(screen.getByRole('button', { name: 'Kur teklifi al' }))
  await screen.findByText('Alacağınız tutar')
  fireEvent.change(screen.getByLabelText('Satılacak tutar (TRY)'), { target: { value: '250' } })
  expect(screen.queryByText('Alacağınız tutar')).toBeNull()
  fireEvent.change(screen.getByLabelText('Satılacak tutar (TRY)'), { target: { value: '500' } })
  fireEvent.click(screen.getByRole('button', { name: 'Kur teklifi al' }))
  await screen.findByText('Alacağınız tutar')
  tradeFails = true
  fireEvent.click(screen.getByRole('button', { name: 'Döviz işlemini onayla →' }))
  await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('İşlem reddedildi'))
  tradeFails = false
  fireEvent.click(screen.getByRole('button', { name: 'Döviz işlemini onayla →' }))
  await screen.findByText(/Döviz işlemi tamamlandı/)
  fireEvent.change(screen.getByLabelText('Satılacak hesap'), { target: { value: usdId } })
  fireEvent.change(screen.getByLabelText('Alınacak hesap'), { target: { value: senderId } })
  expect(screen.getByLabelText('Satılacak tutar (USD)')).toBeTruthy()

  fireEvent.click(screen.getByRole('button', { name: /Harcama geçmişi/ }))
  expect(screen.getByText('Toplam harcama · TRY')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: /Yenile/ }))
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/fx/rates', expect.any(Object)))
}, 20000)

it('shows honest empty states when accounts and histories have no data', async () => {
  vi.resetModules()
  document.body.innerHTML = '<div id="root"></div>'
  sessionStorage.setItem('corebank_access_token', 'empty-jwt')
  vi.stubGlobal('crypto', { randomUUID: () => 'empty-key' })
  vi.stubGlobal('fetch', vi.fn(async (url: string) => ({ ok: true, json: async () => {
    if (url === '/api/accounts?size=100') return { content: [] }
    if (url === '/api/spending') return { items: [], totalsByCurrency: {} }
    if (url.startsWith('/api/accounts/') && !url.endsWith('/ledger')) return {
      id: 'unrelated', customerId: ownerId, currency: 'TRY', balance: 0, availableBalance: 0,
    }
    return []
  } })))
  await act(async () => { await import('./main') })
  await screen.findByText('Finansal görünümünüz')
  expect(screen.getByText('Henüz harcama yok.')).toBeTruthy()
  expect(screen.getByText('Kur sağlayıcısına ulaşılamıyor.')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: /Para transferi/ }))
  expect(screen.getByText('Seçilmedi')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: /Ödemeler/ }))
  expect(screen.getByText('Henüz ödeme bulunmuyor.')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: /Döviz işlemleri/ }))
  expect(screen.getByText('Kur alınamadı. Yenile ile tekrar deneyin.')).toBeTruthy()
  expect(screen.getByText('Henüz döviz işlemi yok.')).toBeTruthy()
  expect(screen.getByRole('button', { name: 'Kur teklifi al' }).hasAttribute('disabled')).toBe(true)
  fireEvent.click(screen.getByRole('button', { name: /Harcama geçmişi/ }))
  expect(screen.getByText('Henüz kesinleşmiş harcama bulunmuyor.')).toBeTruthy()
})

it('rejects bad credentials, authenticates with JWT and logs out', async () => {
  vi.resetModules()
  sessionStorage.clear()
  document.body.innerHTML = '<div id="root"></div>'
  let loginMode: 'non-error' | 'reject' | 'success' = 'non-error'
  const response = (body: unknown, ok = true, status = 200) => ({ ok, status, json: async () => body })
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url === '/api/auth/login') {
      if (loginMode === 'non-error') throw 'network unavailable'
      return loginMode === 'reject'
        ? response({ message: 'Kullanıcı adı veya parola hatalı' }, false, 401)
        : response({ accessToken: 'signed.jwt.token', expiresAt: '2099-01-01T00:00:00Z', username: 'bener', customerId: ownerId, role: 'USER' })
    }
    if (url === '/api/accounts?size=100') return response({ content: [] })
    if (url === '/api/spending') return response({ items: [], totalsByCurrency: {} })
    if (url.startsWith('/api/accounts/') && !url.endsWith('/ledger')) return response({ id: senderId, customerId: ownerId, currency: 'TRY', balance: 0, availableBalance: 0 })
    return response([])
  }))
  await act(async () => { await import('./main') })
  expect(await screen.findByText('Güvenli bankacılık girişi')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Güvenli giriş' }))
  expect((await screen.findByRole('alert')).textContent).toContain('Giriş başarısız')
  loginMode = 'reject'
  fireEvent.change(screen.getByLabelText('Kullanıcı adı'), { target: { value: 'yanlis' } })
  fireEvent.change(screen.getByLabelText('Parola'), { target: { value: 'yanlis' } })
  fireEvent.click(screen.getByRole('button', { name: 'Güvenli giriş' }))
  expect((await screen.findByRole('alert')).textContent).toContain('Kullanıcı adı veya parola hatalı')
  loginMode = 'success'
  fireEvent.change(screen.getByLabelText('Kullanıcı adı'), { target: { value: 'bener' } })
  fireEvent.change(screen.getByLabelText('Parola'), { target: { value: 'Bener123!' } })
  fireEvent.click(screen.getByRole('button', { name: 'Güvenli giriş' }))
  expect(await screen.findByText('Finansal görünümünüz')).toBeTruthy()
  expect(sessionStorage.getItem('corebank_access_token')).toBe('signed.jwt.token')
  fireEvent.click(screen.getByRole('button', { name: 'Çıkış' }))
  expect(await screen.findByText('Güvenli bankacılık girişi')).toBeTruthy()
  expect(sessionStorage.getItem('corebank_access_token')).toBeNull()
})
