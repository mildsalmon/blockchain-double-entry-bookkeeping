import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Blockchain Double-Entry Bookkeeping',
  description: 'Audit workbench for Ethereum accounting journals'
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
