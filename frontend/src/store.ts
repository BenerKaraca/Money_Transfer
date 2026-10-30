import { configureStore, createAsyncThunk, createSlice, isPending, isRejected } from '@reduxjs/toolkit'
import { api, type Account, type BillPayment, type Transfer, type LedgerEntry, type FxRate, type FxQuote, type FxTrade, type SpendingHistory } from './api'

export const loadAccount = createAsyncThunk('bank/loadAccount', api.account)
export const loadAccounts = createAsyncThunk('bank/loadAccounts', api.accounts)
export const loadPayees = createAsyncThunk('bank/loadPayees', api.payees)
export const loadSpending = createAsyncThunk('bank/loadSpending', api.spending)
export const loadLedger = createAsyncThunk('bank/loadLedger', api.ledger)
export const loadPayments = createAsyncThunk('bank/loadPayments', api.payments)
export const loadRates = createAsyncThunk('bank/loadRates', api.rates)
export const loadTrades = createAsyncThunk('bank/loadTrades', api.trades)
export const requestQuote = createAsyncThunk('bank/requestQuote',
  async ({ from, to, amount }: { from: string; to: string; amount: number }) => api.quote(from, to, amount))
export const executeTrade = createAsyncThunk('bank/executeTrade',
  async ({ source, destination, quoteId, key }: { source: string; destination: string; quoteId: string; key: string }) =>
    api.trade(source, destination, quoteId, key))
export const sendTransfer = createAsyncThunk(
  'bank/sendTransfer',
  async ({ source, destination, amount, key }: { source: string; destination: string; amount: number; key: string }) =>
    api.transfer(source, destination, amount, key),
)
export const startPayment = createAsyncThunk(
  'bank/startPayment',
  async ({ accountId, amount, scenario, key }: { accountId: string; amount: number; scenario: string; key: string }) =>
    api.billPayment(accountId, amount, scenario, key),
)
export const refreshPayment = createAsyncThunk('bank/refreshPayment', api.paymentStatus)

type BankState = {
  accounts: Record<string, Account>
  accountList: Account[]
  payees: Account[]
  spending: SpendingHistory
  ledger: LedgerEntry[]
  payments: BillPayment[]
  rates: FxRate[]
  quote: FxQuote | null
  trades: FxTrade[]
  lastTrade: FxTrade | null
  lastTransfer: Transfer | null
  payment: BillPayment | null
  busy: boolean
  error: string | null
}

const initialState: BankState = { accounts: {}, accountList: [], payees: [], spending: { items: [], totalsByCurrency: {} }, ledger: [], payments: [], rates: [], quote: null,
  trades: [], lastTrade: null, lastTransfer: null, payment: null, busy: false, error: null }

const bank = createSlice({
  name: 'bank',
  initialState,
  reducers: { clearError: state => { state.error = null }, clearQuote: state => { state.quote = null } },
  extraReducers: builder => {
    builder
      .addCase(loadAccount.fulfilled, (state, action) => { state.accounts[action.payload.id] = action.payload })
      .addCase(loadAccounts.fulfilled, (state, action) => {
        state.accountList = action.payload.content
        state.accountList.forEach(account => { state.accounts[account.id] = account })
      })
      .addCase(loadPayees.fulfilled, (state, action) => { state.payees = action.payload })
      .addCase(loadSpending.fulfilled, (state, action) => { state.spending = action.payload })
      .addCase(loadLedger.fulfilled, (state, action) => { state.ledger = action.payload })
      .addCase(loadPayments.fulfilled, (state, action) => { state.payments = action.payload })
      .addCase(loadRates.fulfilled, (state, action) => { state.rates = action.payload })
      .addCase(loadTrades.fulfilled, (state, action) => { state.trades = action.payload })
      .addCase(requestQuote.fulfilled, (state, action) => { state.quote = action.payload; state.busy = false })
      .addCase(executeTrade.fulfilled, (state, action) => { state.lastTrade = action.payload; state.quote = null; state.busy = false })
      .addCase(sendTransfer.fulfilled, (state, action) => { state.lastTransfer = action.payload; state.busy = false })
      .addCase(startPayment.fulfilled, (state, action) => { state.payment = action.payload; state.busy = false })
      .addCase(refreshPayment.fulfilled, (state, action) => { state.payment = action.payload })
      .addMatcher(isPending(sendTransfer, startPayment, requestQuote, executeTrade), state => { state.busy = true; state.error = null })
      .addMatcher(isRejected, (state, action) => {
        state.busy = false
        state.error = action.error.message ?? 'İstek başarısız'
      })
  },
})

export const { clearError, clearQuote } = bank.actions
export const store = configureStore({ reducer: { bank: bank.reducer } })
export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
