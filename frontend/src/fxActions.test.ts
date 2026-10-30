import { expect, it, vi } from 'vitest'
import { quoteIfReady, tradeIfQuoted } from './fxActions'
import type { AppDispatch } from './store'
import type { Account, FxQuote } from './api'

it('guards incomplete FX forms and sends ready requests through the store', async () => {
  const dispatch = vi.fn(() => ({ unwrap: async () => ({ id: 'ok' }) })) as unknown as AppDispatch
  const account = { id: 'a', currency: 'TRY' } as Account
  const target = { id: 'b', currency: 'EUR' } as Account
  await quoteIfReady(dispatch, undefined, target, 10)
  await quoteIfReady(dispatch, account, undefined, 10)
  await tradeIfQuoted(dispatch, null, 'a', 'b', 'key')
  expect(dispatch).not.toHaveBeenCalled()
  await quoteIfReady(dispatch, account, target, 10)
  await tradeIfQuoted(dispatch, { id: 'quote' } as FxQuote, 'a', 'b', 'key')
  expect(dispatch).toHaveBeenCalledTimes(2)
})
