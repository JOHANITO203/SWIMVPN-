import React from 'react';
import { ArrowLeft } from 'lucide-react';

// Legal pages in the showcase grammar: light warm "chalk" surface, ink text, hairline
// borders, purple accent, Readex Pro (inherited). Dark footer matching the landing.
export const LegalLayout = ({ children, title }: { children: React.ReactNode; title: string }) => {
  return (
    <div className="min-h-screen flex flex-col bg-[#F4EDE8] text-[#14130F] selection:bg-[#7B57E8]/25">
      <nav className="w-full max-w-3xl mx-auto px-6 py-8">
        <a href="#" className="group inline-flex items-center gap-2 text-[#6B6862] transition-colors hover:text-[#14130F]">
          <ArrowLeft size={18} className="transition-transform group-hover:-translate-x-1" />
          <span className="text-sm font-medium">Retour</span>
        </a>
      </nav>

      <main className="flex-grow w-full max-w-3xl mx-auto px-6 pb-20">
        <div className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#6B6862] mb-3">
          Document légal
        </div>
        <h1 className="text-3xl md:text-5xl font-bold tracking-[-0.035em] leading-[1.02] mb-10">
          {title}
        </h1>

        <div className="rounded-[22px] border border-[#E4DDD8] bg-[#FBF7F5] p-6 md:p-10 shadow-[0_1px_0_rgba(0,0,0,.02),0_24px_60px_-34px_rgba(20,19,15,.28)]">
          <div className="prose prose-neutral max-w-none prose-headings:font-bold prose-headings:tracking-[-0.02em] prose-headings:text-[#14130F] prose-h2:text-[1.35rem] prose-p:text-[#3F3C36] prose-li:text-[#3F3C36] prose-a:text-[#7B57E8] prose-a:no-underline hover:prose-a:underline prose-strong:text-[#14130F]">
            {children}
          </div>
        </div>
      </main>

      <footer className="bg-[#07070C] px-6 py-9 text-[13.5px] text-white/30">
        <div className="max-w-3xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 font-bold tracking-[-0.03em] text-[15px]">
            <span className="h-[18px] w-[18px] rounded-[5px] bg-gradient-to-br from-[#B89AFF] via-[#8A6AF1] to-[#5D3BD8]" />
            <span className="text-white/80">SWIMVPN</span>
          </div>
          <a href="mailto:support@swimvpn.pro" className="text-white/50 hover:text-white transition-colors">Support</a>
          <div>© 2026 · app.swimvpn.pro · Android</div>
        </div>
      </footer>
    </div>
  );
};
