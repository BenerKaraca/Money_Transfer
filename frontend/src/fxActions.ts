import type { Account, FxQuote } from './api'
import { executeTrade, requestQuote, type AppDispatch } from './store'

export async function quoteIfReady(dispatch: AppDispatch, source: Account | undefined,
                                   target: Account | undefined, amount: number) {
  if (!source || !target) return
  await dispatch(requestQuote({ from: source.currency, to: target.currency, amount })).unwrap()
}

export async function tradeIfQuoted(dispatch: AppDispatch, quote: FxQuote | null,
                                    source: string, target: string, key: string) {
  if (!quote) return
  await dispatch(executeTrade({ source, destination: target, quoteId: quote.id, key })).unwrap()
}
