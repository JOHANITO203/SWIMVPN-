import { useCine } from './i18n';

export default function CineFooter() {
  const { t } = useCine();
  return (
    <footer className="relative z-50 border-t border-white/10 bg-black px-5 py-10 sm:px-10">
      <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 sm:flex-row sm:items-center">
        <span className="font-askan text-lg text-white">SWIMVPN</span>
        <p className="text-sm text-white/50">{t.footer.copyright}</p>
        <div className="flex gap-5 text-sm text-white/60">
          <a href="#cine-tech" className="cine-press hover:text-white">{t.nav.technologie}</a>
          <a href="#cine-tarifs" className="cine-press hover:text-white">{t.nav.tarifs}</a>
          <a href="#cine-signup" className="cine-press hover:text-white">{t.download.telechargement}</a>
        </div>
      </div>
      <div className="mx-auto mt-6 flex max-w-6xl flex-wrap gap-x-5 gap-y-2 border-t border-white/5 pt-6 text-[13px] text-white/40">
        <a href="#terms" className="cine-press hover:text-white">{t.footer.terms}</a>
        <a href="#privacy" className="cine-press hover:text-white">{t.footer.privacy}</a>
        <a href="#refund" className="cine-press hover:text-white">{t.footer.refund}</a>
        <a href="#contact" className="cine-press hover:text-white">{t.footer.contact}</a>
      </div>
    </footer>
  );
}
