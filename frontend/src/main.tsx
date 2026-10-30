import { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider, useDispatch, useSelector } from 'react-redux'
import { clearError, clearQuote, loadAccount, loadAccounts, loadLedger, loadPayees, loadPayments, loadRates, loadSpending, loadTrades, refreshPayment, sendTransfer, startPayment, store, type AppDispatch, type RootState } from './store'
import { api, authToken, type Account, type LoginResult } from './api'
import { quoteIfReady, tradeIfQuoted } from './fxActions'
import './style.css'

const senderId = '20000000-0000-0000-0000-000000000001'
const sections = ['Genel bakış', 'Para transferi', 'Ödemeler', 'Döviz işlemleri', 'Harcama geçmişi'] as const
type Section = typeof sections[number]
const money = (value: number | undefined, currency = 'TRY') => value === undefined ? '—' : new Intl.NumberFormat('tr-TR', { style: 'currency', currency }).format(value)
const shortId = (id: string) => id.slice(0, 8).toUpperCase()
const date = (value: string) => new Date(value).toLocaleString('tr-TR', { dateStyle: 'medium', timeStyle: 'short' })

function Dashboard({ user, onLogout }: { user: LoginResult; onLogout: () => void }) {
  const dispatch = useDispatch<AppDispatch>()
  const { accounts, accountList, payees, spending, ledger, payments, rates, quote, trades, lastTrade, lastTransfer, payment, busy, error } = useSelector((state: RootState) => state.bank)
  const [section, setSection] = useState<Section>('Genel bakış')
  const [transferSource, setTransferSource] = useState(senderId)
  const [transferTarget, setTransferTarget] = useState('')
  const [transferAmount, setTransferAmount] = useState('250')
  const [paymentAccount, setPaymentAccount] = useState(senderId)
  const [billAmount, setBillAmount] = useState('120')
  const [scenario, setScenario] = useState('APPROVE')
  const [fxSource, setFxSource] = useState(senderId)
  const [fxTarget, setFxTarget] = useState('20000000-0000-0000-0000-000000000004')
  const [fxAmount, setFxAmount] = useState('500')
  const [transferKey, setTransferKey] = useState(() => crypto.randomUUID())
  const [paymentKey, setPaymentKey] = useState(() => crypto.randomUUID())
  const [tradeKey, setTradeKey] = useState(() => crypto.randomUUID())
  const own = accountList
  const ownTry = own.filter(account => account.currency === 'TRY')
  const source = accounts[fxSource]
  const target = accounts[fxTarget]
  const primary = accounts[senderId]
  const activePayments = payments.filter(item => item.status === 'PENDING' || item.status === 'UNKNOWN').length

  const reload = () => {
    void dispatch(loadAccounts()); void dispatch(loadAccount(senderId)); void dispatch(loadLedger(senderId))
    void dispatch(loadPayees()); void dispatch(loadPayments(senderId)); void dispatch(loadTrades(senderId)); void dispatch(loadSpending())
  }
  useEffect(() => { reload(); void dispatch(loadRates()) }, [dispatch])
  useEffect(() => {
    if (!payment || !['PENDING', 'UNKNOWN'].includes(payment.status)) return
    const timer = window.setInterval(() => { void dispatch(refreshPayment(payment.id)).then(reload) }, 2000)
    return () => window.clearInterval(timer)
  }, [dispatch, payment?.id, payment?.status])
  const transfer = async () => {
    try {
      await dispatch(sendTransfer({ source: transferSource, destination: transferTarget, amount: Number(transferAmount), key: transferKey })).unwrap()
      setTransferKey(crypto.randomUUID()); reload()
    } catch { /* Preserve key for safe retry. */ }
  }
  const pay = async () => {
    try {
      await dispatch(startPayment({ accountId: paymentAccount, amount: Number(billAmount), scenario, key: paymentKey })).unwrap()
      setPaymentKey(crypto.randomUUID()); reload()
    } catch { /* Preserve key for safe retry. */ }
  }
  const getQuote = async () => {
    try { await quoteIfReady(dispatch, source, target, Number(fxAmount)) }
    catch { /* Store shows error. */ }
  }
  const trade = async () => {
    try {
      await tradeIfQuoted(dispatch, quote, fxSource, fxTarget, tradeKey)
      setTradeKey(crypto.randomUUID()); reload()
    } catch { /* Preserve key for safe retry. */ }
  }
  const accountOption = (account: Account) => `${account.customerName ?? 'Hesap'} · ${account.currency} · ${money(account.availableBalance, account.currency)}`
  const changeFx = (kind: 'source' | 'target' | 'amount', value: string) => {
    if (kind === 'source') setFxSource(value)
    if (kind === 'target') setFxTarget(value)
    if (kind === 'amount') setFxAmount(value)
    dispatch(clearQuote())
  }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">B</span><span><strong>BK Bank</strong><small>CORE BANKING SUITE</small></span></div>
      <div className="workspace-label">ÇALIŞMA ALANI</div>
      <nav aria-label="Ana menü">{sections.map((item, index) => <button key={item} className={`nav-item ${section === item ? 'active' : ''}`} onClick={() => { setSection(item); dispatch(clearError()) }}><span className="nav-icon">{['▦', '↗', '▤', '◈', '≡'][index]}</span>{item}</button>)}</nav>
      <div className="sidebar-bottom"><span className="status-dot" /> Servisler çalışıyor <small>Teknik mülakat ortamı</small></div>
    </aside>
    <div className="content-shell">
      <div className="topbar"><span>Bankacılık paneli <span className="topbar-divider">/</span> {section}</span><div className="topbar-right"><span className="environment-pill">● JWT KORUMALI</span><span className="user-name">{user.username}</span><button className="logout-button" onClick={onLogout}>Çıkış</button><span className="avatar">{user.username.slice(0, 2).toUpperCase()}</span></div></div>
      <main>
        <div className="page-heading"><div><div className="eyebrow">BENERBANK / {section.toUpperCase()}</div><h1>{section === 'Genel bakış' ? 'Finansal görünümünüz' : section}</h1><p>{section === 'Genel bakış' ? 'Hesaplarınız, işlemleriniz ve güncel referans kurlar tek yerde.' : section === 'Döviz işlemleri' ? 'TRY, EUR ve USD hesaplarınız arasında dönüşüm yapın.' : section === 'Ödemeler' ? 'Sağlayıcı ödemelerini ve işlem durumlarını takip edin.' : section === 'Para transferi' ? 'Alıcı hesap seçerek transfer oluşturun.' : 'Hesap ve döviz işlemlerinizi inceleyin.'}</p></div><button className="refresh-button" onClick={() => { reload(); void dispatch(loadRates()) }}>↻ &nbsp; Yenile</button></div>
        {error && <div className="alert" role="alert"><span>{error}</span><button onClick={() => dispatch(clearError())} aria-label="Hatayı kapat">×</button></div>}

        {section === 'Genel bakış' && <>
          <section className="hero"><div><span className="hero-label">ANA HESAP · TÜRK LİRASI</span><div className="hero-balance">{money(primary?.balance)}</div><span className="hero-sub">Kullanılabilir bakiye &nbsp; {money(primary?.availableBalance)}</span><div className="hero-actions"><button onClick={() => setSection('Para transferi')}>Para gönder ↗</button><button className="hero-secondary" onClick={() => setSection('Döviz işlemleri')}>Döviz işlemi →</button></div></div><div className="hero-decoration">BB</div></section>
          <div className="summary-grid"><div className="metric"><span>Toplam hesap</span><strong>{own.length}</strong><small>TRY · EUR · USD</small></div><div className="metric"><span>Bekleyen ödemeler</span><strong>{activePayments}</strong><small>SAGA işlem takibi</small></div><div className="metric"><span>Son kur tarihi</span><strong>{rates[0]?.date ?? '—'}</strong><small>Günlük referans kur</small></div></div>
          <div className="section-heading"><div><span className="eyebrow">HESAPLARIM</span><h2>Para birimi bakiyeleri</h2></div><button className="link-button" onClick={() => setSection('Harcama geçmişi')}>Harcama geçmişi →</button></div>
          <div className="account-grid">{own.map(account => <article className="account-card" key={account.id}><div className="account-top"><span className={`currency-badge ${account.currency.toLowerCase()}`}>{account.currency === 'TRY' ? '₺' : account.currency === 'EUR' ? '€' : '$'}</span><span>{account.currency} HESABI</span></div><strong>{money(account.balance, account.currency)}</strong><div className="account-foot"><span>Kullanılabilir {money(account.availableBalance, account.currency)}</span><span>•• {account.id.slice(-4)}</span></div></article>)}</div>
          <div className="overview-grid"><section className="panel"><div className="panel-heading"><h3>Son harcamalar</h3><button className="link-button" onClick={() => setSection('Harcama geçmişi')}>Tümü →</button></div><div className="table-wrap"><table><thead><tr><th>İşlem</th><th>Tarih</th><th className="right">Tutar</th></tr></thead><tbody>{spending.items.slice(0, 5).map(item => <tr key={item.id}><td><span className="transaction-icon debit">↗</span>{item.description}</td><td>{date(item.occurredAt)}</td><td className="right negative">−{money(item.amount, item.currency)}</td></tr>)}{spending.items.length === 0 && <tr><td colSpan={3} className="empty">Henüz harcama yok.</td></tr>}</tbody></table></div></section>
          <section className="panel"><div className="panel-heading"><h3>Referans kurlar</h3><button className="link-button" onClick={() => setSection('Döviz işlemleri')}>Döviz al/sat →</button></div><div className="rate-list">{rates.map(rate => <div className="rate-row" key={`${rate.base}-${rate.quote}`}><div><strong>{rate.base}/{rate.quote}</strong><small>1 {rate.base} karşılığı</small></div><strong>{money(rate.rate, rate.quote)}</strong></div>)}{rates.length === 0 && <div className="empty">Kur sağlayıcısına ulaşılamıyor.</div>}</div><p className="source-note">Frankfurter · günlük referans kur</p></section></div>
        </>}

        {section === 'Para transferi' && <div className="form-layout"><section className="panel form-panel"><div className="panel-heading"><h3>Yeni transfer</h3><span className="step-pill">01 / 02</span></div><div className="form-body"><label>Gönderen hesap<select value={transferSource} onChange={event => setTransferSource(event.target.value)}>{ownTry.map(account => <option key={account.id} value={account.id}>{accountOption(account)}</option>)}</select></label><label>Alıcı hesap<select value={transferTarget} onChange={event => setTransferTarget(event.target.value)}><option value="">Alıcı seçin</option>{payees.map(account => <option key={account.id} value={account.id}>{accountOption(account)}</option>)}</select></label><label>Tutar (TRY)<input type="number" min="0.01" step="0.01" value={transferAmount} onChange={event => setTransferAmount(event.target.value)} /></label><div className="form-hint">Tutar, alıcı hesabına tek veritabanı işlemi içinde aktarılır.</div><button className="primary-button" disabled={busy || !transferTarget || Number(transferAmount) <= 0} onClick={() => void transfer()}>Transfer yap ↗</button></div></section><aside className="side-panel"><div className="side-panel-icon">↗</div><h3>Transfer özeti</h3><div className="summary-line"><span>Gönderen</span><strong>{ownTry.find(account => account.id === transferSource)?.customerName ?? '—'}</strong></div><div className="summary-line"><span>Alıcı</span><strong>{payees.find(account => account.id === transferTarget)?.customerName ?? 'Seçilmedi'}</strong></div><div className="summary-line"><span>Tutar</span><strong>{money(Number(transferAmount))}</strong></div><div className="summary-line"><span>İşlem ücreti</span><strong>{money(0)}</strong></div><div className="summary-total"><span>Toplam</span><strong>{money(Number(transferAmount))}</strong></div><p>Tekrar denemelerde aynı Idempotency-Key kullanılır.</p>{lastTransfer && <div className="success-box">✓ Transfer tamamlandı · {shortId(lastTransfer.id)}</div>}</aside></div>}

        {section === 'Ödemeler' && <><div className="form-layout"><section className="panel form-panel"><div className="panel-heading"><h3>Sağlayıcı ödemesi</h3><span className="step-pill">SAGA AKIŞI</span></div><div className="form-body"><div className="provider-tile"><span className="provider-logo">P</span><div><strong>BK Bank ödeme sağlayıcısı</strong><small>Harici servis · durum doğrulaması etkin</small></div><span className="provider-check">✓</span></div><label>Ödeme hesabı<select value={paymentAccount} onChange={event => setPaymentAccount(event.target.value)}>{ownTry.map(account => <option key={account.id} value={account.id}>{accountOption(account)}</option>)}</select></label><label>Tutar (TRY)<input type="number" min="0.01" step="0.01" value={billAmount} onChange={event => setBillAmount(event.target.value)} /></label><details className="test-options"><summary>İşlem sonucu senaryosu</summary><label>Sağlayıcı davranışı<select value={scenario} onChange={event => setScenario(event.target.value)}><option value="APPROVE">Onayla</option><option value="REJECT">Reddet</option><option value="TIMEOUT_ONCE">İlk yanıtı geciktir</option></select></label><p>Bu seçenek ödeme sağlayıcısının yanıtını test etmek içindir.</p></details><button className="primary-button" disabled={busy || Number(billAmount) <= 0} onClick={() => void pay()}>Ödemeyi başlat →</button></div></section><aside className="side-panel"><div className="side-panel-icon">▤</div><h3>İşlem nasıl ilerler?</h3><ol className="flow-list"><li><strong>Rezervasyon</strong><span>Tutar kullanılabilir bakiyeden ayrılır.</span></li><li><strong>Sağlayıcı yanıtı</strong><span>Harici servis sonucu belirler.</span></li><li><strong>Mutabakat</strong><span>Onay kesinleşir; ret halinde rezervasyon çözülür.</span></li></ol>{payment && <div className="success-box">Son ödeme: {payment.status} · {shortId(payment.id)}</div>}</aside></div><section className="panel history-panel"><div className="panel-heading"><h3>Ödeme geçmişi</h3><span className="muted">Son 50 işlem</span></div><div className="table-wrap"><table><thead><tr><th>İşlem no</th><th>Durum</th><th className="right">Tutar</th></tr></thead><tbody>{payments.map(item => <tr key={item.id}><td className="mono">{shortId(item.id)}</td><td><span className={`status-badge ${item.status.toLowerCase()}`}>{item.status}</span></td><td className="right">{money(item.amount)}</td></tr>)}{payments.length === 0 && <tr><td colSpan={3} className="empty">Henüz ödeme bulunmuyor.</td></tr>}</tbody></table></div></section></>}

        {section === 'Döviz işlemleri' && <><div className="fx-banner"><div><span className="eyebrow">REFERANS KUR</span><h2>Döviz alım satımı</h2><p>Teklif iki dakika geçerlidir. Bakiyeler atomik olarak güncellenir.</p></div><span>€ $ ₺</span></div><div className="form-layout"><section className="panel form-panel"><div className="panel-heading"><h3>Yeni döviz işlemi</h3><span className="step-pill">GÜNCEL KUR</span></div><div className="form-body"><label>Satılacak hesap<select value={fxSource} onChange={event => changeFx('source', event.target.value)}>{own.map(account => <option key={account.id} value={account.id}>{accountOption(account)}</option>)}</select></label><label>Alınacak hesap<select value={fxTarget} onChange={event => changeFx('target', event.target.value)}>{own.filter(account => account.id !== fxSource).map(account => <option key={account.id} value={account.id}>{accountOption(account)}</option>)}</select></label><label>Satılacak tutar ({source?.currency ?? '—'})<input type="number" min="0.01" step="0.01" value={fxAmount} onChange={event => changeFx('amount', event.target.value)} /></label><button className="secondary-button" disabled={busy || !source || !target || source.currency === target.currency || Number(fxAmount) <= 0} onClick={() => void getQuote()}>Kur teklifi al</button>{quote && <div className="quote-box"><span>Alacağınız tutar</span><strong>{money(quote.targetAmount, quote.targetCurrency)}</strong><small>1 {quote.sourceCurrency} = {quote.rate} {quote.targetCurrency} · Kur tarihi {quote.rateDate}</small><small>Teklif bitişi: {date(quote.expiresAt)}</small><button className="primary-button" disabled={busy || Date.now() >= new Date(quote.expiresAt).getTime()} onClick={() => void trade()}>Döviz işlemini onayla →</button></div>}{lastTrade && <div className="success-box">✓ Döviz işlemi tamamlandı · {shortId(lastTrade.id)}</div>}</div></section><aside className="side-panel"><div className="side-panel-icon">◈</div><h3>Referans kurlar</h3><div className="rate-list">{rates.map(rate => <div className="rate-row" key={`${rate.base}-${rate.quote}`}><div><strong>{rate.base}/{rate.quote}</strong><small>{rate.date}</small></div><strong>{money(rate.rate, rate.quote)}</strong></div>)}{rates.length === 0 && <div className="empty">Kur alınamadı. Yenile ile tekrar deneyin.</div>}</div><p>Frankfurter günlük orta kurları kullanılır. Alış/satış makası uygulanmaz.</p></aside></div><section className="panel history-panel"><div className="panel-heading"><h3>Döviz işlem geçmişi</h3><span className="muted">Son 50 işlem</span></div><div className="table-wrap"><table><thead><tr><th>İşlem no</th><th>Satılan</th><th>Alınan</th><th>Kur tarihi</th></tr></thead><tbody>{trades.map(item => <tr key={item.id}><td className="mono">{shortId(item.id)}</td><td>{money(item.sourceAmount, accounts[item.sourceAccountId]?.currency)}</td><td className="positive">{money(item.targetAmount, accounts[item.destinationAccountId]?.currency)}</td><td>{item.rateDate}</td></tr>)}{trades.length === 0 && <tr><td colSpan={4} className="empty">Henüz döviz işlemi yok.</td></tr>}</tbody></table></div></section></>}

        {section === 'Harcama geçmişi' && <><div className="summary-grid">{Object.entries(spending.totalsByCurrency).map(([currency, total]) => <div className="metric" key={currency}><span>Toplam harcama · {currency}</span><strong>{money(total, currency)}</strong><small>Son 100 kesinleşmiş işlem</small></div>)}</div><section className="panel history-panel"><div className="panel-heading"><h3>Harcama geçmişi</h3><span className="muted">{spending.items.length} kayıt</span></div><div className="table-wrap"><table><thead><tr><th>Tür</th><th>Açıklama</th><th>Referans</th><th>Tarih</th><th className="right">Tutar</th></tr></thead><tbody>{spending.items.map(item => <tr key={item.id}><td><span className={`status-badge ${item.type.toLowerCase()}`}>{item.type === 'BILL_PAYMENT' ? 'Fatura' : item.type === 'FX_EXCHANGE' ? 'Döviz' : 'Transfer'}</span></td><td>{item.description}</td><td className="mono">{shortId(item.id)}</td><td>{date(item.occurredAt)}</td><td className="right negative">−{money(item.amount, item.currency)}</td></tr>)}{spending.items.length === 0 && <tr><td colSpan={5} className="empty">Henüz kesinleşmiş harcama bulunmuyor.</td></tr>}</tbody></table></div></section></>}
        <footer>BK Bank · Eğitim ve teknik mülakat ortamı · Gerçek para işlemi yapılmaz.</footer>
      </main>
    </div>
  </div>
}

function LoginPage({ onLogin }: { onLogin: (user: LoginResult) => void }) {
  const [username, setUsername] = useState('bener')
  const [password, setPassword] = useState('Bener123!')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setError('')
    try { const result = await api.login(username, password); authToken.set(result.accessToken); onLogin(result) }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Giriş başarısız') }
    finally { setBusy(false) }
  }
  return <div className="login-page"><section className="login-card"><div className="brand login-brand"><span className="brand-mark">B</span><span><strong>BK Bank</strong><small>SECURE CORE BANKING</small></span></div><div className="eyebrow">JWT · SPRING SECURITY</div><h1>Güvenli bankacılık girişi</h1><p>Hesaplarınız ve finansal işlemleriniz rol ve hesap sahipliği kurallarıyla korunur.</p>{error && <div className="alert" role="alert">{error}</div>}<form onSubmit={submit}><label>Kullanıcı adı<input value={username} onChange={event => setUsername(event.target.value)} autoComplete="username" /></label><label>Parola<input type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" /></label><button className="primary-button" disabled={busy}>{busy ? 'Doğrulanıyor…' : 'Güvenli giriş'}</button></form><div className="login-note">Demo kullanıcı: <strong>bener</strong> · Parola: <strong>Bener123!</strong></div></section></div>
}

function BankApp() {
  const [user, setUser] = useState<LoginResult | null>(() => authToken.get() ? { accessToken: authToken.get()!, expiresAt: '', username: 'bener', customerId: '', role: 'USER' } : null)
  const logout = () => { authToken.clear(); setUser(null) }
  return user ? <Dashboard user={user} onLogout={logout} /> : <LoginPage onLogin={setUser} />
}

createRoot(document.getElementById('root')!).render(<Provider store={store}><BankApp /></Provider>)
