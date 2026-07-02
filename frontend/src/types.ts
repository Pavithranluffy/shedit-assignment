export type Role = 'member' | 'librarian';

export type Member = {
  id: number;
  name: string;
  email: string;
  tier: string;
  balance: number;
  activeLoansCount: number;
};

export type Book = {
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

export type Loan = {
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

export type WaitlistEntry = {
  id: number;
  bookId: number;
  bookTitle: string;
  memberId: number;
  memberName: string;
  status: string;
  reservedCopyBarcode?: string;
  daysRemaining?: number;
};
