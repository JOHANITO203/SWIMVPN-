import React from 'react';
import { ArrowLeft } from 'lucide-react';
import { Footer } from '../landing/Footer';

export const LegalLayout = ({ children, title }: { children: React.ReactNode; title: string }) => {
  return (
    <div className="bg-[#05070A] min-h-screen text-slate-200 selection:bg-cyan-500/30 selection:text-cyan-200 flex flex-col">
      <nav className="w-full max-w-5xl mx-auto px-6 py-8 flex items-center justify-between">
        <a href="#" className="flex items-center gap-3 group hover:opacity-80 transition-opacity">
          <ArrowLeft size={20} className="text-cyan-400 group-hover:-translate-x-1 transition-transform" />
          <span className="text-white font-black tracking-tighter text-lg md:text-xl italic">BACK</span>
        </a>
      </nav>

      <main className="flex-grow container mx-auto max-w-4xl px-6 py-12 md:py-20">
        <div className="text-cyan-400 font-mono text-xs font-bold tracking-[0.4em] mb-4 uppercase">
          // LEGAL_DOCUMENT
        </div>
        <h1 className="text-4xl md:text-6xl font-black text-white mb-12 tracking-tighter uppercase italic leading-[0.9]">
          {title}
        </h1>

        <div className="prose prose-invert prose-slate max-w-none prose-headings:font-black prose-headings:uppercase prose-headings:tracking-tighter prose-headings:italic prose-h2:text-white prose-a:text-cyan-400 hover:prose-a:text-cyan-300">
          {children}
        </div>
      </main>

      <Footer />
    </div>
  );
};
