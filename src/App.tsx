import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Shield, 
  Power, 
  Globe, 
  User, 
  Settings, 
  HelpCircle, 
  ChevronRight, 
  Plus, 
  CheckCircle, 
  X,
  CreditCard,
  MessageSquare,
  ArrowRight,
  Clipboard,
  QrCode,
  Link as LinkIcon
} from 'lucide-react';
import { UserAccess, ServerNode } from './types';

// --- Settings & Context ---

const SharkLogo = ({ className = "w-8 h-8", color = "currentColor" }: any) => (
  <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className={className}>
    <path d="M2 20C2 20 6 20 8 18C10 16 9 11 13 8C17 5 22 4 22 4C22 4 21 9 18 13C15 17 10 16 8 18C6 20 2 20 2 20Z" fill={color} />
    <path d="M8 18C6 20 2 20 2 20L5 17C6 16 7 17 8 18Z" fill={color} fillOpacity="0.5" />
  </svg>
);

const translations: any = {
  RU: {
    home: 'Главная',
    connect: 'Подключить',
    connecting: 'Подключение...',
    connected: 'Подключено',
    disconnected: 'Отключено',
    servers: 'Серверы',
    settings: 'Настройки',
    profile: 'Профиль',
    subscription: 'Подписка',
    import: 'Импорт',
    trial: 'Пробный период',
    daysLeft: 'дней осталось',
    encryption: 'Шифрование активно',
    touchToProtect: 'Нажмите для защиты',
    selectedServer: 'Выбранный сервер',
    support: 'Поддержка'
  },
  EN: {
    home: 'Home',
    connect: 'Connect',
    connecting: 'Connecting...',
    connected: 'Connected',
    disconnected: 'Disconnected',
    servers: 'Servers',
    settings: 'Settings',
    profile: 'Profile',
    subscription: 'Subscription',
    import: 'Import',
    trial: 'Trial',
    daysLeft: 'days left',
    encryption: 'Encryption active',
    touchToProtect: 'Touch to protect connection',
    selectedServer: 'Selected Server',
    support: 'Support'
  },
  FR: {
    home: 'Accueil',
    connect: 'Connecter',
    connecting: 'Connexion...',
    connected: 'Connecté',
    disconnected: 'Déconnecté',
    servers: 'Serveurs',
    settings: 'Paramètres',
    profile: 'Profil',
    subscription: 'Abonnement',
    import: 'Importer',
    trial: 'Essai',
    daysLeft: 'jours restants',
    encryption: 'Chiffrement actif',
    touchToProtect: 'Toucher pour protéger',
    selectedServer: 'Serveur sélectionné',
    support: 'Support'
  }
};

const Button = ({ children, onClick, variant = 'primary', className = '' }: any) => {
  const variants = {
    primary: 'bg-minimal-primary text-white font-bold hover:bg-opacity-90',
    secondary: 'bg-minimal-border text-minimal-text hover:bg-black/5',
    outline: 'border border-minimal-border text-minimal-text hover:bg-black/5',
    ghost: 'text-minimal-text-secondary hover:text-minimal-text',
    dark: 'bg-minimal-text-bold text-white font-semibold rounded-full hover:bg-opacity-90 shadow-sm'
  };
  return (
    <button 
      onClick={onClick}
      className={`px-4 py-3 rounded-xl transition-all active:scale-95 flex items-center justify-center gap-2 ${variants[variant as keyof typeof variants]} ${className}`}
    >
      {children}
    </button>
  );
};

// --- Screens ---

const Onboarding = ({ onFinish }: { onFinish: () => void }) => {
  const [step, setStep] = useState(0);
  const slides = [
    {
      title: "Welcome to SWIMVPN+",
      description: "Fast, secure access to everything you love. Designed for privacy and speed.",
      icon: <SharkLogo className="w-16 h-16 text-minimal-primary" />
    },
    {
      title: "7 Days Free Trial",
      description: "Start exploring today. No credit card required to begin your 7-day trial.",
      icon: <CheckCircle className="w-16 h-16 text-minimal-primary" />
    },
    {
      title: "Simple Activation",
      description: "Import yours access link, scan a QR code, or enter a coupon to get started.",
      icon: <Plus className="w-16 h-16 text-minimal-primary" />
    }
  ];

  return (
    <div className="h-full flex flex-col items-center justify-center p-8 app-surface text-center">
      <AnimatePresence mode="wait">
        <motion.div
          key={step}
          initial={{ opacity: 0, scale: 0.9, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.9, y: -20 }}
          className="flex flex-col items-center gap-6"
        >
          <div className="p-6 app-bg rounded-full mb-4">
            {slides[step].icon}
          </div>
          <h1 className="text-3xl app-heading leading-tight">{slides[step].title}</h1>
          <p className="app-text-secondary text-lg leading-relaxed">{slides[step].description}</p>
        </motion.div>
      </AnimatePresence>

      <div className="flex gap-2 mt-12">
        {slides.map((_, i) => (
          <div key={i} className={`h-1.5 rounded-full transition-all ${i === step ? 'w-8 bg-minimal-primary' : 'w-2 app-border'}`} />
        ))}
      </div>

      <div className="flex flex-col gap-3 w-full mt-auto">
        <Button 
          onClick={() => step < slides.length - 1 ? setStep(step + 1) : onFinish()}
          className="w-full py-4 text-xl uppercase tracking-tighter font-black"
        >
          {step === slides.length - 1 ? 'Start Free Trial' : 'Continue'}
        </Button>
      </div>
    </div>
  );
};

const HomeScreen = ({ user, servers, onNavigate, config }: any) => {
  const t = translations[config.language] || translations.EN;
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [selectedServer, setSelectedServer] = useState(servers[0]);
  const [showImport, setShowImport] = useState(false);

  useEffect(() => {
    if (servers.length > 0 && !selectedServer) {
        setSelectedServer(servers[0]);
    }
  }, [servers, selectedServer]);

  const toggleConnection = () => {
    if (isConnected) {
      setIsConnected(false);
    } else {
      setIsConnecting(true);
      setTimeout(() => {
        setIsConnecting(false);
        setIsConnected(true);
      }, 1500);
    }
  };

  return (
    <div className="h-full flex flex-col p-6 app-surface relative overflow-hidden text-minimal-text">
       <header className="flex justify-between items-center mb-12">
        <div className="flex items-center gap-2">
          <div className="w-10 h-10 bg-minimal-primary rounded-xl flex items-center justify-center">
            <SharkLogo className="w-7 h-7 text-white" />
          </div>
          <span className="font-extrabold text-xl tracking-tighter app-text-bold">SWIMVPN+</span>
        </div>
        <div className="flex gap-2">
          <button onClick={() => onNavigate('profile')} className="p-2 hover:bg-black/5 dark:hover:bg-white/5 rounded-lg transition-colors"><User className="w-6 h-6 app-text-secondary" /></button>
        </div>
      </header>

      <div className="flex-1 flex flex-col items-center justify-center">
        <div className="trial-badge bg-minimal-trial-bg dark:bg-dark-trial-bg text-minimal-trial-text dark:text-dark-trial-text px-4 py-1 rounded-full text-xs font-bold uppercase mb-8 shadow-sm">
          {t.trial}: 6 {t.daysLeft}
        </div>

        <motion.div 
          animate={{ scale: isConnecting ? [1, 1.05, 1] : 1 }}
          transition={{ repeat: Infinity, duration: 2 }}
          className="relative"
        >
          {/* Pulse Effect */}
          <AnimatePresence>
            {isConnected && (
              <motion.div 
                initial={{ scale: 0.8, opacity: 0 }}
                animate={{ scale: 2, opacity: 0 }}
                transition={{ repeat: Infinity, duration: 2 }}
                className="absolute inset-0 bg-minimal-success rounded-full z-0"
              />
            )}
          </AnimatePresence>

          <button 
            onClick={toggleConnection}
            disabled={isConnecting}
            className={`relative z-10 w-44 h-44 rounded-full border-8 flex flex-col items-center justify-center transition-all duration-500 app-surface ${isConnected ? 'border-minimal-success shadow-lg shadow-minimal-success/10' : 'app-border shadow-md shadow-black/5'}`}
          >
             <div className="w-14 h-14 border-4 border-minimal-primary rounded-full relative flex items-center justify-center">
               <div className="absolute top-[-10px] left-1/2 -translate-x-1/2 w-1 h-5 bg-minimal-primary rounded-full" />
             </div>
          </button>
        </motion.div>

        <div className="mt-12 text-center">
          <h2 className="text-2xl font-bold mb-1 app-text-bold">
            {isConnecting ? t.connecting : isConnected ? t.connected : t.disconnected}
          </h2>
          <p className="app-text-secondary text-sm">
            {isConnected ? t.encryption : t.touchToProtect}
          </p>
        </div>
      </div>

      <div className="mt-auto space-y-4">
        <div 
          onClick={() => onNavigate('servers')}
          className="app-card p-4 rounded-2xl flex items-center justify-between cursor-pointer transition-all"
        >
          <div className="flex items-center gap-3">
             <div className="w-10 h-10 app-bg rounded-full flex items-center justify-center app-text-secondary font-bold text-xs">
                {selectedServer?.country.substring(0, 2).toUpperCase() || 'RU'}
             </div>
            <div>
              <p className="text-[10px] app-text-muted uppercase font-bold tracking-wider">{t.selectedServer}</p>
              <p className="font-bold app-text">{selectedServer?.country || 'Russia'}, {selectedServer?.city || 'Moscow #4'}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-minimal-success font-bold text-sm">24ms</span>
            <ChevronRight className="app-text-secondary w-4 h-4" />
          </div>
        </div>

        <div className="flex items-center justify-center">
           <Button onClick={() => setShowImport(true)} variant="dark" className="w-full py-4 px-8">
            <Plus className="w-5 h-5" /> {t.import}
          </Button>
        </div>
      </div>

      {/* Import Modal Overlay */}
      <AnimatePresence>
        {showImport && (
          <motion.div 
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            className="absolute inset-0 bg-white z-50 p-6 flex flex-col text-minimal-text"
          >
             <div className="flex justify-between items-center mb-8">
               <h2 className="text-2xl font-bold text-minimal-text-bold">Import Access</h2>
               <button onClick={() => setShowImport(false)} className="p-2 hover:bg-black/5 rounded-full"><X className="text-minimal-text-secondary" /></button>
             </div>
             
             <div className="grid grid-cols-2 gap-4">
               {[
                 { icon: <LinkIcon />, label: 'Paste Link' },
                 { icon: <QrCode />, label: 'Scan QR' },
                 { icon: <Clipboard />, label: 'Clipboard' },
                 { icon: <Plus />, label: 'Enter Code' }
               ].map((item, i) => (
                 <button key={i} className="bg-minimal-bg p-6 rounded-2xl flex flex-col items-center gap-3 hover:bg-minimal-primary hover:text-white transition-all border border-minimal-border">
                   {item.icon}
                   <span className="font-bold text-sm">{item.label}</span>
                 </button>
               ))}
             </div>

             <div className="mt-auto p-6 bg-minimal-primary/5 rounded-3xl border border-minimal-primary/10 mb-6">
               <h3 className="font-bold text-minimal-primary mb-2">Need access?</h3>
               <p className="text-sm text-minimal-text-secondary mb-4 leading-relaxed">If you don't have a plan yet, you can purchase premium access from our support team.</p>
               <Button onClick={() => setShowImport(false)} className="w-full">Get Premium</Button>
             </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

const ServersScreen = ({ servers, onBack }: any) => {
  return (
    <div className="h-full flex flex-col app-bg">
      <header className="p-6 flex items-center gap-4 border-b app-border app-surface">
        <button onClick={onBack} className="p-1 hover:bg-black/5 dark:hover:bg-white/5 rounded-full"><ArrowRight className="rotate-180 app-text-secondary" /></button>
        <h2 className="text-xl app-heading">Servers</h2>
      </header>
      
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {servers.map((server: ServerNode) => (
          <div key={server.id} className="app-card p-4 rounded-2xl flex items-center justify-between hover:border-minimal-primary transition-all cursor-pointer group">
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 app-bg rounded-full flex items-center justify-center border app-border app-text-secondary font-bold text-xs uppercase">
                {server.country.substring(0, 2)}
              </div>
              <div>
                <p className="font-bold app-text">{server.country}</p>
                <p className="text-xs app-text-secondary">{server.city}</p>
              </div>
            </div>
            <div className="flex items-center gap-3">
               <span className="text-minimal-success font-mono text-[10px] font-bold">24ms</span>
               <ChevronRight className="w-4 h-4 app-text-muted" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

const ProfileScreen = ({ user, onBack, onNavigate }: any) => {
  return (
    <div className="h-full flex flex-col app-bg p-6">
      <header className="flex items-center gap-4 mb-8">
        <button onClick={onBack} className="p-1 hover:bg-black/5 dark:hover:bg-white/5 rounded-full"><ArrowRight className="rotate-180 app-text-secondary" /></button>
        <h2 className="text-xl app-heading">Profile</h2>
      </header>

      <div className="space-y-6">
        <div className="app-card p-6 rounded-[2.5rem] relative overflow-hidden">
          <div className="absolute top-4 right-4">
            <span className="text-[10px] px-2 py-1 bg-minimal-trial-bg dark:bg-dark-trial-bg text-minimal-trial-text dark:text-dark-trial-text rounded-full font-bold uppercase tracking-wider">{user?.planType || 'TRIAL'}</span>
          </div>
          <p className="app-text-muted text-[10px] font-bold uppercase tracking-widest mb-1">User Identification</p>
          <h3 className="text-xl app-heading mb-4">{user?.userNumber || 'ID #882412'}</h3>
          <p className="text-sm app-text-secondary">v1-trial-user@swimvpn.ru</p>
        </div>

        <div className="space-y-4">
          <div className="flex flex-col gap-1">
            <p className="text-[10px] font-bold app-text-muted uppercase tracking-widest ml-2 mb-2">Account Statistics</p>
            <div className="app-card rounded-3xl p-4 space-y-4">
              <div className="flex justify-between items-center">
                <span className="text-sm app-text-secondary">Plan Expires</span>
                <span className="text-sm font-bold text-red-500">144 hours left</span>
              </div>
              <div className="w-full h-1 app-bg rounded-full overflow-hidden">
                <div className="h-full bg-minimal-primary w-[80%] rounded-full" />
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm app-text-secondary">Active Devices</span>
                <span className="text-sm font-bold app-text-bold">1 / 3 active</span>
              </div>
            </div>
          </div>

          <div className="space-y-2">
            <p className="text-[10px] font-bold app-text-muted uppercase tracking-widest ml-2 mb-2">Management</p>
            <div className="app-card rounded-3xl overflow-hidden">
              {[
                { id: 'subscription', icon: <CreditCard className="w-5 h-5" />, label: 'Subscription' },
                { id: 'import', icon: <Plus className="w-5 h-5" />, label: 'Redeem Code' },
                { id: 'settings', icon: <Settings className="w-5 h-5" />, label: 'Settings' },
                { id: 'support', icon: <HelpCircle className="w-5 h-5" />, label: 'Support Channel' }
              ].map((item, i) => (
                <div 
                  key={i} 
                  onClick={() => {
                    if (item.id === 'support') onNavigate('support');
                    if (item.id === 'import') onNavigate('import');
                    if (item.id === 'settings') onNavigate('settings');
                    if (item.id === 'subscription') onNavigate('subscription');
                  }}
                  className={`p-4 flex items-center justify-between hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer transition-colors ${i !== 3 ? 'border-b app-border' : ''}`}
                >
                  <div className="flex items-center gap-4 app-text-secondary font-medium">
                    {item.icon}
                    <span className="text-sm font-bold">{item.label}</span>
                  </div>
                  <ChevronRight className="w-4 h-4 app-text-muted" />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="mt-auto">
        <Button variant="outline" className="w-full py-4 text-red-500 border-red-100 dark:border-red-900/30 hover:bg-red-50 dark:hover:bg-red-900/10 font-bold">Sign Out</Button>
      </div>
    </div>
  );
};

const ImportAccessScreen = ({ onBack }: any) => {
  const [code, setCode] = useState('');
  
  const handleImport = (method: string) => {
    alert(`Starting import via ${method}`);
  };

  return (
    <div className="h-full flex flex-col app-bg">
      <header className="p-6 flex items-center gap-4 border-b app-border sticky top-0 app-surface backdrop-blur-md z-10">
        <button onClick={onBack} className="p-1 hover:bg-black/5 dark:hover:bg-white/5 rounded-full"><ArrowRight className="rotate-180 app-text-secondary" /></button>
        <h2 className="text-xl app-heading">Import Access</h2>
      </header>

      <div className="flex-1 overflow-y-auto p-6 space-y-8">
        <div className="space-y-4">
          <p className="text-xs font-bold app-text-muted uppercase tracking-widest ml-2">Quick Actions</p>
          <div className="grid grid-cols-2 gap-4">
               {[
                 { icon: <LinkIcon />, label: 'Paste Link', color: 'bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400' },
                 { icon: <QrCode />, label: 'Scan QR', color: 'bg-purple-50 dark:bg-purple-900/20 text-purple-600 dark:text-purple-400' },
                 { icon: <Clipboard />, label: 'Clipboard', color: 'bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400' },
                 { icon: <Plus />, label: 'Manual', color: 'bg-orange-50 dark:bg-orange-900/20 text-orange-600 dark:text-orange-400' }
               ].map((item, i) => (
                 <button 
                  key={i} 
                  onClick={() => handleImport(item.label)}
                  className="app-card p-6 rounded-[2rem] flex flex-col items-center gap-3 hover:shadow-md transition-all"
                >
                   <div className={`w-12 h-12 ${item.color} rounded-2xl flex items-center justify-center`}>{item.icon}</div>
                   <span className="font-bold text-sm app-text">{item.label}</span>
                 </button>
               ))}
           </div>
        </div>

        <div className="space-y-4">
          <p className="text-xs font-bold app-text-muted uppercase tracking-widest ml-2">Manual Entry</p>
          <div className="space-y-4 app-card p-6 rounded-[2rem]">
            <input 
              type="text" 
              placeholder="Enter access code or link..." 
              value={code}
              onChange={(e) => setCode(e.target.value)}
              className="w-full app-bg border app-border p-4 rounded-xl outline-none focus:border-minimal-primary transition-colors text-sm"
            />
            <Button onClick={() => handleImport('Manual Input')} className="w-full font-black uppercase tracking-widest text-[10px]">Activate Access</Button>
          </div>
        </div>

        <div className="p-8 bg-minimal-primary/5 dark:bg-minimal-primary/10 rounded-[2.5rem] border border-minimal-primary/10 text-center">
           <SharkLogo className="w-12 h-12 text-minimal-primary mx-auto mb-4" />
           <h3 className="app-heading mb-2 text-lg">No access code?</h3>
           <p className="text-sm app-text-secondary mb-6 leading-relaxed">Contact your provider or visit our official Telegram bot to purchase a premium subscription.</p>
           <Button variant="outline" className="w-full font-bold">Visit Store</Button>
        </div>
      </div>
    </div>
  );
};

const SupportScreen = ({ onBack }: any) => {
  const faqs = [
    { q: "How to connect?", a: "Just tap the center button on the home screen. It will find the best server for you automatically." },
    { q: "My connection is slow", a: "Try switching to a different server location or change the protocol in settings." },
    { q: "Payment issues", a: "If your subscription didn't update, please send your User ID to our support bot." },
    { q: "Is it safe?", a: "Yes, we use VLESS Reality encryption which is virtually undetectable." }
  ];

  return (
    <div className="h-full flex flex-col app-bg">
      <header className="p-6 flex items-center gap-4 border-b app-border sticky top-0 app-surface backdrop-blur-md z-10">
        <button onClick={onBack} className="p-1 hover:bg-black/5 dark:hover:bg-white/5 rounded-full"><ArrowRight className="rotate-180 app-text-secondary" /></button>
        <h2 className="text-xl app-heading">Help & Support</h2>
      </header>

      <div className="flex-1 overflow-y-auto p-6 space-y-8">
        <section className="space-y-4">
          <p className="text-xs font-bold app-text-muted uppercase tracking-widest ml-2">Common Questions</p>
          <div className="space-y-3">
            {faqs.map((faq, i) => (
              <div key={i} className="app-card p-5 rounded-[2rem]">
                <p className="font-bold app-text mb-2 text-sm">{faq.q}</p>
                <p className="text-xs app-text-secondary leading-relaxed">{faq.a}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="space-y-4">
          <p className="text-xs font-bold app-text-muted uppercase tracking-widest ml-2">Direct Contact</p>
          <div className="grid grid-cols-1 gap-3">
             <button className="flex items-center gap-4 p-5 app-card rounded-[2rem] hover:shadow-md transition-all group border-none bg-white dark:bg-dark-surface">
               <div className="w-12 h-12 bg-[#0088cc]/10 text-[#0088cc] rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
                 <MessageSquare className="w-6 h-6" />
               </div>
               <div className="text-left">
                 <p className="font-bold app-text text-sm">Telegram Support</p>
                 <p className="text-[11px] app-text-secondary">@swimvpn_robot</p>
               </div>
               <ChevronRight className="ml-auto w-4 h-4 app-text-muted" />
             </button>

             <button className="flex items-center gap-4 p-5 app-card rounded-[2rem] hover:shadow-md transition-all group border-none bg-white dark:bg-dark-surface">
               <div className="w-12 h-12 bg-red-50 dark:bg-red-900/10 text-red-500 rounded-2xl flex items-center justify-center group-hover:scale-110 transition-transform">
                 <HelpCircle className="w-6 h-6" />
               </div>
               <div className="text-left">
                 <p className="font-bold app-text text-sm">Email Support</p>
                 <p className="text-[11px] app-text-secondary">support@swimvpn.ru</p>
               </div>
               <ChevronRight className="ml-auto w-4 h-4 app-text-muted" />
             </button>
          </div>
        </section>

        <div className="mt-8 text-center text-[10px] app-text-muted uppercase tracking-widest font-black">
          App Version 1.0.4 • Build 882
        </div>
      </div>
    </div>
  );
};

const SubscriptionScreen = ({ onBack }: any) => {
  const plans = [
    { id: '1m', name: '1 Month', price: '$5.99', desc: 'Base Protection' },
    { id: '3m', name: '3 Months', price: '$14.99', desc: 'Secure Explorer', popular: true },
    { id: '1y', name: '1 Year', price: '$39.99', desc: 'Unlimited Privacy', savings: '45%' },
  ];

  return (
    <div className="h-full flex flex-col bg-white dark:bg-dark-bg">
      <header className="p-6 flex items-center gap-4 border-b app-border sticky top-0 app-surface z-10">
        <button onClick={onBack} className="p-1 hover:bg-black/5 dark:hover:bg-white/5 rounded-full"><ArrowRight className="rotate-180 app-text-secondary" /></button>
        <h2 className="text-xl app-heading">Premium Subscription</h2>
      </header>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        <div className="p-6 bg-minimal-primary rounded-[2.5rem] text-white flex flex-col items-center text-center">
           <SharkLogo className="w-12 h-12 mb-4" />
           <h3 className="text-2xl font-black mb-2">Upgrade to Pro</h3>
           <p className="text-sm opacity-90 text-white/80 leading-relaxed">Unlock high-speed servers, unlimited traffic, and priority support.</p>
        </div>

        <div className="space-y-4">
          {plans.map((plan) => (
            <button key={plan.id} className={`w-full p-6 rounded-3xl border-2 transition-all text-left flex items-center justify-between relative overflow-hidden ${plan.popular ? 'border-minimal-primary bg-minimal-primary/5 app-surface' : 'app-border app-surface'}`}>
              {plan.popular && (
                <div className="absolute top-0 right-0 bg-minimal-primary text-white text-[9px] font-black px-4 py-1 rounded-bl-xl uppercase tracking-widest">Popular</div>
              )}
              {plan.savings && (
                <div className="absolute top-0 right-0 bg-minimal-success text-white text-[9px] font-black px-4 py-1 rounded-bl-xl uppercase tracking-widest">Save {plan.savings}</div>
              )}
              <div>
                <p className="app-text-muted text-[10px] font-black uppercase tracking-widest mb-1">{plan.desc}</p>
                <h4 className="app-text-bold text-lg font-bold">{plan.name}</h4>
              </div>
              <div className="text-right">
                <p className="app-text-bold text-xl font-black">{plan.price}</p>
              </div>
            </button>
          ))}
        </div>

        <div className="app-card p-6 rounded-3xl">
          <div className="flex items-center gap-3 mb-4">
            <Plus className="w-5 h-5 text-minimal-primary" />
            <h4 className="app-text-bold font-bold text-sm">Have a coupon?</h4>
          </div>
          <div className="flex gap-2">
            <input type="text" placeholder="XXXX-XXXX" className="flex-1 app-bg app-border border p-3 rounded-xl outline-none focus:border-minimal-primary text-sm" />
            <Button className="px-6">Apply</Button>
          </div>
        </div>

        <div className="text-center p-4">
          <p className="text-[10px] app-text-muted leading-relaxed">
            Payments are processed securely via our official Telegram bot or local payment partners in Russia.
          </p>
        </div>
      </div>

      <div className="p-6 border-t app-border app-surface sticky bottom-0">
        <Button variant="dark" className="w-full py-4 uppercase tracking-tighter font-black">Continue to Payment</Button>
      </div>
    </div>
  );
};

const SettingsScreen = ({ config, onUpdateConfig, onBack }: any) => {
  const t = translations[config.language] || translations.EN;

  const saveSettings = () => {
    onBack();
  };

  const resetSettings = () => {
    onUpdateConfig({
      language: 'RU',
      themeMode: 'Light',
      vpnMode: 'Tunnel',
      autoConnect: true,
      killSwitch: false,
      protocol: 'VLESS',
      port: 'Auto',
      dnsMode: 'System'
    });
  };

  const Section = ({ title, children }: any) => (
    <section className="mb-8">
      <h3 className="text-[10px] font-bold app-text-muted uppercase tracking-widest mb-4 ml-2">{title}</h3>
      <div className="app-card rounded-3xl overflow-hidden shadow-sm">
        {children}
      </div>
    </section>
  );

  const SettingRow = ({ label, desc, children, noBorder }: any) => (
    <div className={`p-5 flex items-center justify-between ${!noBorder ? 'border-b app-border' : ''} hover:bg-black/5 dark:hover:bg-white/5 transition-colors`}>
      <div className="flex-1 pr-4">
        <p className="font-bold app-text text-sm">{label}</p>
        {desc && <p className="text-[11px] app-text-secondary leading-tight mt-1">{desc}</p>}
      </div>
      <div>{children}</div>
    </div>
  );

  const Toggle = ({ active, onToggle }: any) => (
    <button 
      onClick={onToggle}
      className={`w-11 h-6 rounded-full relative transition-colors ${active ? 'bg-minimal-primary' : 'bg-minimal-border dark:bg-minimal-border/20'}`}
    >
      <div className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-all shadow-sm ${active ? 'left-6' : 'left-1'}`} />
    </button>
  );

  const Select = ({ value, options, onChange }: any) => (
    <select 
      value={value} 
      onChange={(e) => onChange(e.target.value)}
      className="bg-transparent text-sm font-black text-minimal-primary outline-none text-right cursor-pointer"
    >
      {options.map((opt: any) => <option key={typeof opt === 'string' ? opt : opt.value} value={typeof opt === 'string' ? opt : opt.value}>{typeof opt === 'string' ? opt : opt.label}</option>)}
    </select>
  );

  return (
    <div className="h-full flex flex-col app-bg">
      <header className="p-6 flex items-center justify-between border-b app-border sticky top-0 app-surface backdrop-blur-md z-10">
        <div className="flex items-center gap-4">
          <button onClick={onBack} className="p-1 hover:bg-black/5 dark:hover:bg-white/5 rounded-full"><ArrowRight className="rotate-180 app-text-secondary" /></button>
          <h2 className="text-xl app-heading">{t.settings}</h2>
        </div>
        <button onClick={resetSettings} className="text-xs font-black text-minimal-primary hover:opacity-70 uppercase tracking-widest">Reset</button>
      </header>
      
      <div className="flex-1 overflow-y-auto p-6 pb-32">
        <Section title="Application">
          <SettingRow label="Language" desc="Select your preferred UI language">
            <Select 
              value={config.language} 
              options={[{label: 'Русский', value: 'RU'}, {label: 'English', value: 'EN'}, {label: 'Français', value: 'FR'}]} 
              onChange={(val: string) => onUpdateConfig({...config, language: val})} 
            />
          </SettingRow>
          <SettingRow label="Theme" desc="Light for clarity, Dark for comfort" noBorder>
            <Select 
              value={config.themeMode} 
              options={['Light', 'Dark', 'System']} 
              onChange={(val: string) => onUpdateConfig({...config, themeMode: val})} 
            />
          </SettingRow>
        </Section>

        <Section title="VPN Engine">
          <SettingRow label="Tunnel Mode" desc="Tunnel (Global) vs Proxy (Local)">
             <Select 
              value={config.vpnMode} 
              options={['Tunnel', 'Proxy']} 
              onChange={(val: string) => onUpdateConfig({...config, vpnMode: val})} 
            />
          </SettingRow>
          <SettingRow label="Auto-connect" desc="Connect on app startup">
            <Toggle active={config.autoConnect} onToggle={() => onUpdateConfig({...config, autoConnect: !config.autoConnect})} />
          </SettingRow>
          <SettingRow label="Kill Switch" desc="Block internet if VPN drops" noBorder>
            <Toggle active={config.killSwitch} onToggle={() => onUpdateConfig({...config, killSwitch: !config.killSwitch})} />
          </SettingRow>
        </Section>

        <Section title="Network Fine-tune">
          <SettingRow label="Protocol" desc="Recommended: VLESS Reality">
            <Select 
              value={config.protocol} 
              options={['VLESS', 'Reality', 'Shadowsocks']} 
              onChange={(val: string) => onUpdateConfig({...config, protocol: val})} 
            />
          </SettingRow>
          <SettingRow label="DNS Settings" desc="Custom domain resolution" noBorder>
             <Select 
              value={config.dnsMode} 
              options={['System', 'Google', 'Cloudflare']} 
              onChange={(val: string) => onUpdateConfig({...config, dnsMode: val})} 
            />
          </SettingRow>
        </Section>
      </div>

      <div className="p-6 app-surface border-t app-border sticky bottom-0">
        <Button onClick={saveSettings} variant="dark" className="w-full py-4 uppercase tracking-[0.2em] text-[10px] font-black shadow-lg">Confirm Changes</Button>
      </div>
    </div>
  );
};

// --- Main App ---

export default function App() {
  const [loading, setLoading] = useState(true);
  const [onboardingDone, setOnboardingDone] = useState(false);
  const [screen, setScreen] = useState('home');
  const [user, setUser] = useState<UserAccess | null>(null);
  const [servers, setServers] = useState<ServerNode[]>([]);
  
  const [config, setConfig] = useState({
    language: 'RU',
    themeMode: 'Light',
    vpnMode: 'Tunnel',
    autoConnect: true,
    killSwitch: false,
    protocol: 'VLESS',
    port: 'Auto',
    dnsMode: 'System'
  });

  useEffect(() => {
    const isDark = config.themeMode === 'Dark' || (config.themeMode === 'System' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.classList.toggle('dark', isDark);
  }, [config.themeMode]);

  useEffect(() => {
    const hasSeen = localStorage.getItem('onboarding_seen');
    if (hasSeen) setOnboardingDone(true);

    const init = async () => {
      try {
        const res = await fetch('/api/trial/start', { 
          method: 'POST', 
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ deviceId: 'emulator-123' })
        });
        const data = await res.json();
        setUser({
            ...data,
            trialExpiresAt: data.trialExpiresAt,
            subscriptionExpiresAt: data.trialExpiresAt,
            userNumber: data.userNumber,
            planType: data.planType
        });
        setServers(data.availableServers);
        setLoading(false);
      } catch (err) {
        console.error('Failed to init app', err);
        setLoading(false);
      }
    };

    init();
  }, []);

  const handleFinishOnboarding = () => {
    localStorage.setItem('onboarding_seen', 'true');
    setOnboardingDone(true);
  };

  if (loading) {
    return (
      <div className="h-screen w-screen bg-minimal-bg dark:bg-dark-bg flex flex-col items-center justify-center p-8 transition-colors duration-500">
        <motion.div 
          animate={{ scale: [1, 1.05, 1], rotate: [0, 5, -5, 0] }}
          transition={{ repeat: Infinity, duration: 3 }}
          className="w-28 h-28 bg-white dark:bg-dark-surface rounded-[2.5rem] border border-minimal-border dark:border-dark-border flex items-center justify-center shadow-2xl mb-8"
        >
          <SharkLogo className="w-16 h-16 text-minimal-primary" />
        </motion.div>
        <p className="app-text-bold font-black tracking-[0.4em] uppercase text-[10px]">SWIMVPN+</p>
        <div className="mt-12 flex gap-1">
          <div className="w-1.5 h-1.5 bg-minimal-primary rounded-full animate-bounce" style={{ animationDelay: '0s' }} />
          <div className="w-1.5 h-1.5 bg-minimal-primary rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
          <div className="w-1.5 h-1.5 bg-minimal-primary rounded-full animate-bounce" style={{ animationDelay: '0.4s' }} />
        </div>
      </div>
    );
  }

  return (
    <div className="h-screen w-screen bg-minimal-bg dark:bg-dark-bg flex items-center justify-center font-sans overflow-hidden transition-all duration-300">
      {/* Phone Frame */}
      <div className="relative w-full max-w-[390px] aspect-[9/19.5] max-h-[844px] bg-white dark:bg-dark-surface rounded-[3.5rem] border-[10px] border-minimal-border dark:border-dark-border shadow-[0_20px_60px_rgba(0,0,0,0.1)] overflow-hidden scale-90 sm:scale-100">
        {/* Status Bar */}
        <div className="absolute top-0 left-0 w-full h-10 flex justify-between items-center px-10 z-50 pointer-events-none">
          <span className="text-[12px] font-bold app-text">12:30</span>
          <div className="flex gap-1.5 items-center">
            <div className="w-3.5 h-3.5 border border-minimal-border dark:border-dark-border rounded-full" />
            <div className="w-3.5 h-3.5 border border-minimal-border dark:border-dark-border rounded-full" />
            <div className="w-6 h-3 border border-minimal-border dark:border-dark-border rounded-sm" />
          </div>
        </div>

        {/* Dynamic Island */}
        <div className="absolute top-3 left-1/2 -translate-x-1/2 w-28 h-7 bg-minimal-border/30 dark:bg-dark-border/40 rounded-full z-50" />

        <div className="h-full pt-10 relative z-10">
          {!onboardingDone ? (
            <Onboarding onFinish={handleFinishOnboarding} />
          ) : (
            <AnimatePresence mode="wait">
              <motion.div
                key={screen}
                initial={{ x: 20, opacity: 0 }}
                animate={{ x: 0, opacity: 1 }}
                exit={{ x: -20, opacity: 0 }}
                transition={{ duration: 0.3, ease: "circOut" }}
                className="h-full"
              >
                {screen === 'home' && <HomeScreen user={user} servers={servers} onNavigate={setScreen} config={config} />}
                {screen === 'servers' && <ServersScreen servers={servers} onBack={() => setScreen('home')} />}
                {screen === 'profile' && <ProfileScreen user={user} onBack={() => setScreen('home')} onNavigate={setScreen} />}
                {screen === 'settings' && <SettingsScreen config={config} onUpdateConfig={setConfig} onBack={() => setScreen('profile')} />}
                {screen === 'support' && <SupportScreen onBack={() => setScreen('profile')} />}
                {screen === 'import' && <ImportAccessScreen onBack={() => setScreen('home')} />}
                {screen === 'subscription' && <SubscriptionScreen onBack={() => setScreen('profile')} />}
                
                {/* Navigation Bar (Simulated) */}
                <div className="absolute bottom-2 left-1/2 -translate-x-1/2 w-32 h-1 bg-minimal-border dark:bg-dark-border rounded-full" />
              </motion.div>
            </AnimatePresence>
          )}
        </div>
      </div>

      {/* Desktop Info */}
      <div className="hidden xl:flex flex-col gap-6 p-12 max-w-sm ml-8">
        <div className="p-8 bg-white rounded-3xl border border-minimal-border shadow-sm">
          <h2 className="text-2xl font-bold mb-2 text-minimal-text-bold tracking-tight">SWIMVPN+</h2>
          <p className="text-minimal-text-secondary text-sm mb-8 leading-relaxed">
            Clean Minimalist Architecture. 
            Optimized for V1 deployment and developer focus.
          </p>
          <div className="space-y-4">
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 bg-minimal-primary/10 rounded-xl flex items-center justify-center text-minimal-primary"><Globe className="w-5 h-5"/></div>
              <div>
                <p className="font-bold text-sm text-minimal-text">Backend Integrated</p>
                <p className="text-xs text-minimal-text-muted">Real-time API simulation</p>
              </div>
            </div>
            <div className="flex items-center gap-4">
              <div className="w-10 h-10 bg-minimal-trial-bg rounded-xl flex items-center justify-center text-minimal-trial-text"><Shield className="w-5 h-5"/></div>
              <div>
                <p className="font-bold text-sm text-minimal-text">Secure & Fast</p>
                <p className="text-xs text-minimal-text-muted">Encrypted traffic by default</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
