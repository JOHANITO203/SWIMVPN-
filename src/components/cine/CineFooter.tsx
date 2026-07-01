import { useCine } from './i18n';

export default function CineFooter() {
  const { t } = useCine();
  return (
    <footer className="relative z-50 border-t border-white/15 bg-black px-6 py-10 sm:px-10 lg:px-16">
      <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 sm:flex-row sm:items-center">
        <span className="font-askan text-xl tracking-wider text-white">SWIMVPN</span>
        <p className="text-sm text-white/50">{t.footer.copyright}</p>
        <div className="flex flex-wrap gap-5 text-xs uppercase tracking-widest text-white/60">
          <a href="#cine-tech" className="transition-colors hover:text-white">{t.nav.technologie}</a>
          <a href="#cine-tarifs" className="transition-colors hover:text-white">{t.nav.tarifs}</a>
          <a href="#cine-faq" className="transition-colors hover:text-white">{t.nav.faq}</a>
          <a href="#cine-signup" className="transition-colors hover:text-white">{t.download.telechargement}</a>
        </div>
      </div>
      <div className="mx-auto mt-6 flex max-w-6xl flex-wrap gap-x-5 gap-y-2 border-t border-white/10 pt-6 text-[13px] text-white/40">
        <a href="#terms" className="transition-colors hover:text-white">{t.footer.terms}</a>
        <a href="#privacy" className="transition-colors hover:text-white">{t.footer.privacy}</a>
        <a href="#refund" className="transition-colors hover:text-white">{t.footer.refund}</a>
        <a href="#contact" className="transition-colors hover:text-white">{t.footer.contact}</a>
      </div>
    </footer>
  );
}
