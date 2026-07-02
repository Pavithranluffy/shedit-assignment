import { useEffect, useMemo, useState } from 'react';

type Role = 'member' | 'librarian';

type Member = {
  id: number;
  name: string;
  email: string;
  tier: string;
  balance: number;
  activeLoansCount: number;
};

type Book = {
  id: number;
  title: string;
  author: string;
  isbn: string;
  replacementCost: number;
  totalCopies: number;
  availableCopies: number;
  reservedCopies: number;
  waitingListCount: number;
  copies: Array<{ id: number; barcode: string; status: string }>;
};

type Loan = {
  id: number;
  bookTitle: string;
  copyBarcode: string;
  copyId: number;
  memberId: number;
  memberName: string;
  borrowDate: string;
  dueDate: string;
  returnDate: string | null;
  feeCharged: number;
  isOverdue: boolean;
};

type WaitlistEntry = {
  id: number;
  bookId: number;
  bookTitle: string;
  memberId: number;
  memberName: string;
  status: string;
  reservedCopyBarcode?: string;
  daysRemaining?: number;
};

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(text || 'Request failed');
  }
  return text ? (JSON.parse(text) as T) : ({} as T);
}

export default function App() {
  const [role, setRole] = useState<Role>('member');
  const [members, setMembers] = useState<Member[]>([]);
  const [books, setBooks] = useState<Book[]>([]);
  const [activeLoans, setActiveLoans] = useState<Loan[]>([]);
  const [waitlist, setWaitlist] = useState<WaitlistEntry[]>([]);
  const [clock, setClock] = useState<string>('');
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null);
  const [memberLoans, setMemberLoans] = useState<Loan[]>([]);
  const [memberWaitlist, setMemberWaitlist] = useState<WaitlistEntry[]>([]);
  const [status, setStatus] = useState('Ready');
  const [bookForm, setBookForm] = useState({ title: '', author: '', isbn: '', replacementCost: '10.00' });
  const [copyForm, setCopyForm] = useState({ bookId: '' });
  const [settleAmount, setSettleAmount] = useState('5.00');

  const loadData = async () => {
    try {
      const [membersData, booksData, loansData, waitlistData, clockData] = await Promise.all([
        requestJson<Member[]>('/api/members'),
        requestJson<Book[]>('/api/books'),
        requestJson<Loan[]>('/api/loans/active'),
        requestJson<WaitlistEntry[]>('/api/waitlist'),
        requestJson<string>('/api/clock'),
      ]);
      setMembers(membersData);
      setBooks(booksData);
      setActiveLoans(loansData);
      setWaitlist(waitlistData);
      setClock(clockData);
      if (!selectedMemberId && membersData[0]) {
        setSelectedMemberId(membersData[0].id);
      }
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Failed to load data');
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  useEffect(() => {
    const loadMemberViews = async () => {
      if (!selectedMemberId) return;
      try {
        const [memberLoansData, memberWaitlistData] = await Promise.all([
          requestJson<Loan[]>(`/api/members/${selectedMemberId}/loans`),
          requestJson<WaitlistEntry[]>(`/api/members/${selectedMemberId}/waitlist`),
        ]);
        setMemberLoans(memberLoansData);
        setMemberWaitlist(memberWaitlistData);
      } catch (error) {
        setStatus(error instanceof Error ? error.message : 'Failed to load member data');
      }
    };
    void loadMemberViews();
  }, [selectedMemberId]);

  const selectedMember = useMemo(() => members.find((member) => member.id === selectedMemberId) ?? null, [members, selectedMemberId]);

  const handleBorrow = async (bookId: number) => {
    if (!selectedMemberId) return;
    try {
      await requestJson('/api/borrow', { method: 'POST', body: JSON.stringify({ memberId: selectedMemberId, bookId }) });
      setStatus('Borrowed successfully');
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Borrow failed');
    }
  };

  const handleReturn = async (copyId: number) => {
    try {
      await requestJson(`/api/return/${copyId}`, { method: 'POST' });
      setStatus('Return recorded');
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Return failed');
    }
  };

  const handleRenew = async (loanId: number) => {
    try {
      await requestJson(`/api/renew/${loanId}`, { method: 'POST' });
      setStatus('Renewal approved');
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Renewal failed');
    }
  };

  const handleJoinWaitlist = async (bookId: number) => {
    if (!selectedMemberId) return;
    try {
      await requestJson('/api/waitlist/join', { method: 'POST', body: JSON.stringify({ memberId: selectedMemberId, bookId }) });
      setStatus('Added to waiting list');
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Join waitlist failed');
    }
  };

  const handleCancelWaitlist = async (entryId: number) => {
    try {
      await requestJson(`/api/waitlist/cancel/${entryId}`, { method: 'POST' });
      setStatus('Waitlist entry cancelled');
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Cancel failed');
    }
  };

  const handleCollect = async (entryId: number) => {
    if (!selectedMemberId) return;
    try {
      await requestJson(`/api/waitlist/collect/${entryId}/member/${selectedMemberId}`, { method: 'POST' });
      setStatus('Reservation collected');
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Collection failed');
    }
  };

  const handleAddBook = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      await requestJson('/api/books', { method: 'POST', body: JSON.stringify(bookForm) });
      setStatus('Book added');
      setBookForm({ title: '', author: '', isbn: '', replacementCost: '10.00' });
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Could not add book');
    }
  };

  const handleAddCopy = async (event: React.FormEvent) => {
    event.preventDefault();
    try {
      await requestJson(`/api/books/${copyForm.bookId}/copies`, { method: 'POST', body: JSON.stringify({ barcode: `COPY-${Date.now()}` }) });
      setStatus('Copy added');
      setCopyForm({ bookId: '' });
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Could not add copy');
    }
  };

  const handleMarkLost = async (copyId: number) => {
    try {
      await requestJson(`/api/copies/${copyId}/lost`, { method: 'POST' });
      setStatus('Copy marked lost');
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Mark lost failed');
    }
  };

  const handleMarkDamaged = async (copyId: number) => {
    try {
      await requestJson(`/api/copies/${copyId}/damaged`, { method: 'POST' });
      setStatus('Copy marked damaged');
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Mark damaged failed');
    }
  };

  const handleSettle = async (memberId: number) => {
    try {
      await requestJson(`/api/members/${memberId}/settle`, { method: 'POST', body: JSON.stringify({ amount: settleAmount }) });
      setStatus('Fees settled');
      await loadData();
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Settle failed');
    }
  };

  return (
    <div className="app-shell">
      <header>
        <div>
          <h1>Schedit Community Library</h1>
          <p>Simple lending flow for members and staff.</p>
        </div>
        <div className="toolbar">
          <label>
            Acting as
            <select value={role} onChange={(event) => setRole(event.target.value as Role)}>
              <option value="member">Member</option>
              <option value="librarian">Librarian</option>
            </select>
          </label>
          <label>
            Member
            <select value={selectedMemberId ?? ''} onChange={(event) => setSelectedMemberId(Number(event.target.value))}>
              {members.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.name} ({member.tier})
                </option>
              ))}
            </select>
          </label>
          <div className="pill">Library clock: {clock}</div>
        </div>
      </header>

      <p className="status">{status}</p>

      <div className="grid">
        <section className="panel">
          <h2>Catalog</h2>
          {books.map((book) => (
            <article key={book.id} className="card">
              <div className="card-header">
                <strong>{book.title}</strong>
                <span>{book.author}</span>
              </div>
              <p>{book.isbn}</p>
              <p>Copies: {book.totalCopies} · Available: {book.availableCopies} · Reserved: {book.reservedCopies} · Waiting: {book.waitingListCount}</p>
              <div className="actions">
                <button disabled={!selectedMemberId} onClick={() => void handleBorrow(book.id)}>Borrow</button>
                <button disabled={!selectedMemberId} onClick={() => void handleJoinWaitlist(book.id)}>Join waitlist</button>
              </div>
            </article>
          ))}
        </section>

        <section className="panel">
          {role === 'member' ? (
            <>
              <h2>My dashboard</h2>
              {selectedMember ? (
                <div className="summary">
                  <p><strong>{selectedMember.name}</strong></p>
                  <p>Tier: {selectedMember.tier}</p>
                  <p>Balance: ${selectedMember.balance.toFixed(2)}</p>
                  <p>Active loans: {selectedMember.activeLoansCount}</p>
                </div>
              ) : null}

              <h3>Active loans</h3>
              {memberLoans.map((loan) => (
                <article key={loan.id} className="card">
                  <p>{loan.bookTitle}</p>
                  <p>Due {loan.dueDate} · Overdue {loan.isOverdue ? 'yes' : 'no'}</p>
                  <div className="actions">
                    <button onClick={() => void handleReturn(loan.copyId)}>Return</button>
                    <button onClick={() => void handleRenew(loan.id)}>Renew</button>
                  </div>
                </article>
              ))}

              <h3>My waitlist</h3>
              {memberWaitlist.map((entry) => (
                <article key={entry.id} className="card">
                  <p>{entry.bookTitle} · {entry.status}</p>
                  <div className="actions">
                    {entry.status === 'RESERVED' ? <button onClick={() => void handleCollect(entry.id)}>Collect</button> : null}
                    {entry.status !== 'FULFILLED' && entry.status !== 'CANCELLED' ? <button onClick={() => void handleCancelWaitlist(entry.id)}>Cancel</button> : null}
                  </div>
                </article>
              ))}
            </>
          ) : (
            <>
              <h2>Librarian tools</h2>
              <form onSubmit={handleAddBook} className="stack">
                <h3>Add a book</h3>
                <input value={bookForm.title} onChange={(event) => setBookForm({ ...bookForm, title: event.target.value })} placeholder="Title" required />
                <input value={bookForm.author} onChange={(event) => setBookForm({ ...bookForm, author: event.target.value })} placeholder="Author" required />
                <input value={bookForm.isbn} onChange={(event) => setBookForm({ ...bookForm, isbn: event.target.value })} placeholder="ISBN" required />
                <input type="number" value={bookForm.replacementCost} onChange={(event) => setBookForm({ ...bookForm, replacementCost: event.target.value })} placeholder="Replacement cost" step="0.01" required />
                <button type="submit">Add book</button>
              </form>

              <form onSubmit={handleAddCopy} className="stack">
                <h3>Add a copy</h3>
                <select value={copyForm.bookId} onChange={(event) => setCopyForm({ ...copyForm, bookId: event.target.value })} required>
                  <option value="">Select a book</option>
                  {books.map((book) => (
                    <option key={book.id} value={book.id}>{book.title}</option>
                  ))}
                </select>
                <button type="submit">Add copy</button>
              </form>

              <h3>Current waitlist</h3>
              {waitlist.map((entry) => (
                <article key={entry.id} className="card">
                  <p>{entry.bookTitle} · {entry.memberName} · {entry.status}</p>
                </article>
              ))}

              <h3>Active loans</h3>
              {activeLoans.map((loan) => (
                <article key={loan.id} className="card">
                  <p>{loan.bookTitle} · {loan.memberName} · due {loan.dueDate}</p>
                  <div className="actions">
                    <button onClick={() => void handleMarkLost(loan.copyId)}>Mark lost</button>
                    <button onClick={() => void handleMarkDamaged(loan.copyId)}>Mark damaged</button>
                  </div>
                </article>
              ))}

              <h3>Fees</h3>
              {members.map((member) => (
                <article key={member.id} className="card">
                  <p>{member.name}: ${member.balance.toFixed(2)}</p>
                  <div className="actions">
                    <input value={settleAmount} onChange={(event) => setSettleAmount(event.target.value)} />
                    <button onClick={() => void handleSettle(member.id)}>Settle</button>
                  </div>
                </article>
              ))}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
