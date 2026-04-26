import React from 'react';

export const SharkLogo = ({ className = "w-6 h-6" }: { className?: string }) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M12 2L9 7H15L12 2Z" fill="currentColor" />
    <path d="M2 13C4 11 6 10 9 10C12 10 14 11 16 13C18 15 20 16 22 16" />
    <path d="M4 17C6 15 8 14 11 14C14 14 16 15 18 17" opacity="0.5" />
  </svg>
);