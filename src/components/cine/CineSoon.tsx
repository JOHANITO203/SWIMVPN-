/** Écran placeholder pour les vues pas encore bâties (livraison écran par écran). */
export default function CineSoon({ title }: { title: string }) {
  return (
    <section className="relative flex min-h-dvh items-center justify-center overflow-hidden bg-black px-5">
      <div
        className="pointer-events-none absolute inset-0"
        style={{ background: 'radial-gradient(120% 80% at 50% -10%, rgba(255,255,255,0.06), transparent 60%)' }}
        aria-hidden
      />
      <div className="relative z-50 text-center">
        <p className="cine-fade-up text-xs uppercase tracking-[0.22em] text-white/55" style={{ animationDelay: '0.1s' }}>
          {title}
        </p>
        <h2
          className="cine-blur-up font-askan mt-4 text-5xl text-white sm:text-7xl"
          style={{ letterSpacing: '-0.05em', animationDelay: '0.25s' }}
        >
          Bientôt.
        </h2>
        <p className="cine-fade-up mt-5 text-white/60" style={{ animationDelay: '0.45s' }}>
          Cet écran arrive — on les bâtit un par un.
        </p>
        <a
          href="#cine"
          className="cine-fade-up mt-8 inline-block text-sm text-white/70 underline-offset-4 hover:underline"
          style={{ animationDelay: '0.6s' }}
        >
          ← Retour au hero
        </a>
      </div>
    </section>
  );
}
