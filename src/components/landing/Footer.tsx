import React from 'react';

export const Footer = () => {
  return (
    <footer className="py-12 border-t border-white/5 px-6 bg-slate-950">
      <div className="container mx-auto flex flex-col md:flex-row justify-between items-center gap-8">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-gradient-to-br from-cyan-400 to-blue-600 rounded-lg" />
          <span className="text-white font-black tracking-tighter text-xl italic">SWIMVPN+</span>
        </div>
        
        <div className="flex gap-8 text-slate-500 text-sm font-semibold">
          <a href="#" className="hover:text-cyan-400 transition-colors">Privacy</a>
          <a href="#" className="hover:text-cyan-400 transition-colors">Terms</a>
          <a href="#" className="hover:text-cyan-400 transition-colors">Contact</a>
        </div>
        
        <div className="text-slate-600 text-sm">
          © {new Date().getFullYear()} SwimVPN+. All rights reserved.
        </div>
      </div>
    </footer>
  );
};
