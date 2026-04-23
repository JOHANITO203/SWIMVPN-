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
  Link as LinkIcon,
  Languages,
  Cpu,
  Zap,
  Network
} from 'lucide-react';
import { UserAccess, ServerNode } from './types';

// --- Settings & Context ---

const SharkLogo = ({ className = "w-8 h-8", color = "currentColor" }: any) => (
  <svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg" className={className}>
    {/* Menacing Shark - Shark Silhouette Strike Pose */}
    <path 
      d="M10 60C10 60 20 50 35 48C50 46 80 48 95 35C85 25 70 15 45 15C20 15 5 35 10 60Z" 
      fill={color} 
    />
    {/* Upper Aggressive Jaw with Teeth silhouettes */}
    <path 
      d="M95 35C95 35 85 37 75 42L85 50L95 35ZM82 46L80 48L78 45L82 46Z" 
      fill={color} 
    />
    {/* Lower Menacing Jaw */}
    <path 
      d="M75 50L88 62C78 72 58 78 40 75C25 72 10 60 10 60L25 58L40 55L75 50Z" 
      fill={color} 
    />
    {/* Sharp Dorsal Fin */}
    <path 
      d="M40 16C40 16 48 0 65 10C60 14 52 20 50 22L40 16Z" 
      fill={color} 
    />
    {/* Menacing Slit Eye */}
    <path d="M62 34L72 32C71 31 69 31 62 34Z" fill="white" />
    <path d="M64 34L70 33" stroke="white" strokeWidth="0.5" strokeLinecap="round" />
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
  const baseClass = "px-6 py-4 rounded-[2rem] transition-all active:scale-95 flex items-center justify-center gap-3";
  const variants = {
    primary: "app-btn-primary",
    secondary: "app-btn-secondary",
    outline: "border-2 app-border app-text font-black uppercase tracking-widest text-[10px] hover:app-bg",
    ghost: "app-text-secondary hover:app-text-bold font-black uppercase tracking-widest text-[10px]",
    dark: "app-btn-primary shadow-2xl"
  };
  return (
    <button onClick={onClick} className={`${baseClass} ${variants[variant as keyof typeof variants]} ${className}`}>
      {children}
    </button>
  );
};

// --- Screens ---

const Onboarding = ({ onFinish }: { onFinish: () => void }) => {
  const [step, setStep] = useState(0);
  const slides = [
    {
      title: "SWIMVPN+",
      description: "Fast, secure access to everything you love. Designed for privacy and speed.",
      icon: <SharkLogo className="w-16 h-16 text-minimal-accent dark:text-dark-accent" />
    },
    {
      title: "PRO TRIAL",
      description: "Start exploring today. No credit card required to begin your 7-day trial.",
      icon: <CheckCircle className="w-16 h-16 text-minimal-accent dark:text-dark-accent" />
    },
    {
      title: "GLOBAL ACCESS",
      description: "Import yours access link, scan a QR code, or enter a coupon to get started.",
      icon: <Globe className="w-16 h-16 text-minimal-accent dark:text-dark-accent" />
    }
  ];

  return (
    <div className="h-full flex flex-col items-center justify-center p-10 app-surface text-center">
      <AnimatePresence mode="wait">
        <motion.div
          key={step}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          className="flex flex-col items-center gap-8"
        >
          <div className="p-10 bg-minimal-bg dark:bg-dark-surface/50 rounded-[3rem] mb-6 shadow-sm border app-border">
            {slides[step].icon}
          </div>
          <h1 className="text-4xl app-heading leading-tight uppercase font-black">{slides[step].title}</h1>
          <p className="app-text-secondary text-sm leading-relaxed max-w-[240px] font-medium">{slides[step].description}</p>
        </motion.div>
      </AnimatePresence>

      <div className="flex gap-2 mt-12 mb-auto">
        {slides.map((_, i) => (
          <div key={i} className={`h-1.5 rounded-full transition-all duration-500 ${i === step ? 'w-10 bg-minimal-accent dark:bg-dark-accent' : 'w-3 bg-minimal-border dark:bg-dark-border opacity-50'}`} />
        ))}
      </div>

      <div className="flex flex-col gap-3 w-full pb-10">
        <Button 
          onClick={() => step < slides.length - 1 ? setStep(step + 1) : onFinish()}
          className="w-full"
        >
          {step === slides.length - 1 ? 'GET STARTED' : 'CONTINUE'}
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
  const [showPlusMenu, setShowPlusMenu] = useState(false);
  const [uptime, setUptime] = useState(0);

  // Derive states
  const hasAccess = user && (user.trialExpiresAt || user.subscriptionExpiresAt);
  const isTrialActive = user?.planType === 'TRIAL' && new Date(user.trialExpiresAt) > new Date();
  const isTrialExpired = user?.planType === 'TRIAL' && new Date(user.trialExpiresAt) <= new Date();

  useEffect(() => {
    if (servers.length > 0 && !selectedServer) {
      setSelectedServer(servers[0]);
    }
  }, [servers, selectedServer]);

  useEffect(() => {
    let interval: any;
    if (isConnected) {
      interval = setInterval(() => {
        setUptime(prev => prev + 1);
      }, 1000);
    } else {
      setUptime(0);
    }
    return () => clearInterval(interval);
  }, [isConnected]);

  const formatTime = (seconds: number) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h > 0 ? h + ':' : ''}${m < 10 ? '0' + m : m}:${s < 10 ? '0' + s : s}`;
  };

  const toggleConnection = () => {
    if (!hasAccess || isTrialExpired) {
      setShowPlusMenu(true);
      return;
    }

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

  const getStatusText = () => {
    if (!hasAccess) return config.language === 'RU' ? 'Нет доступа' : 'No Access';
    if (isTrialExpired) return config.language === 'RU' ? 'Пробный период истек' : 'Trial Expired';
    if (isConnecting) return t.connecting;
    if (isConnected) return t.connected;
    return t.disconnected;
  };

  const getStatusSubtext = () => {
    if (!hasAccess) return config.language === 'RU' ? 'Импортируйте ключ для начала' : 'Import key to start';
    if (isTrialExpired) return config.language === 'RU' ? 'Продлите подписку для защиты' : 'Renew subscription to protect';
    if (isConnected) return `IP: 142.250.190.46 • ${t.encryption}`;
    return t.touchToProtect;
  };

  const plusMenuItems = [
    { icon: <LinkIcon className="w-5 h-5" />, label: config.language === 'RU' ? 'Вставить ссылку' : 'Paste Link' },
    { icon: <QrCode className="w-5 h-5" />, label: config.language === 'RU' ? 'Сканировать QR' : 'Scan QR' },
    { icon: <Plus className="w-5 h-5" />, label: config.language === 'RU' ? 'Введите код' : 'Enter Code' },
    { icon: <Clipboard className="w-5 h-5" />, label: config.language === 'RU' ? 'Из буфера' : 'From Clipboard' }
  ];

  return (
    <div className="h-full flex flex-col p-6 app-surface relative overflow-hidden transition-colors font-sans">
      {/* Background Decor */}
      <div className="absolute top-[-10%] right-[-10%] w-64 h-64 bg-minimal-accent/10 dark:bg-dark-accent/5 rounded-full blur-[100px] pointer-events-none" />
      
      {/* Header */}
      <header className="flex justify-between items-center mb-10 z-20">
        <div className="flex items-center gap-3">
          <div className="w-12 h-12 app-btn-primary rounded-2xl flex items-center justify-center shadow-2xl">
            <SharkLogo className="w-8 h-8 text-white dark:text-dark-bg" />
          </div>
          <span className="font-black text-2xl tracking-tighter app-text-bold uppercase">SwimVPN+</span>
        </div>
        <button 
          onClick={() => onNavigate('profile')} 
          className="w-12 h-12 app-surface border-2 app-border rounded-full flex items-center justify-center shadow-sm active:scale-90 transition-all app-text"
        >
          <User className="w-6 h-6" />
        </button>
      </header>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col items-center justify-center relative">
        {/* Absolute Background Elements */}
        {isConnected && (
           <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full aspect-square bg-minimal-accent/5 dark:bg-dark-accent/10 rounded-full blur-3xl z-0 pointer-events-none" />
        )}

        {/* Trial Badge */}
        {isTrialActive && (
          <motion.div 
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            className="z-10 trial-badge bg-minimal-trial-bg dark:bg-dark-trial-bg text-minimal-trial-text dark:text-dark-trial-text px-6 py-2 rounded-full text-[10px] font-black uppercase tracking-[0.2em] mb-12 shadow-lg border-2 app-border"
          >
            {t.trial}: 6 {t.daysLeft}
          </motion.div>
        )}

        {/* Connection Circle */}
        <div className="relative z-10 mb-16">
          <motion.div 
            animate={{ scale: isConnecting ? [1, 1.02, 1] : 1 }}
            transition={{ repeat: Infinity, duration: 2 }}
            className="relative"
          >
            <AnimatePresence>
              {isConnected && (
                <motion.div 
                  initial={{ scale: 0.9, opacity: 0 }}
                  animate={{ scale: 1.5, opacity: 0 }}
                  transition={{ repeat: Infinity, duration: 3, ease: "linear" }}
                  className="absolute inset-0 border-4 border-minimal-success/20 rounded-full z-0"
                />
              )}
            </AnimatePresence>

            <button 
              onClick={toggleConnection}
              disabled={isConnecting}
              className={`relative z-10 w-56 h-56 rounded-full border-[12px] flex flex-col items-center justify-center transition-all duration-700 app-surface shadow-2xl ${isConnected ? 'border-minimal-success shadow-minimal-success/10' : isTrialExpired || !hasAccess ? 'border-red-500 shadow-red-500/10' : 'app-border dark:shadow-none'}`}
            >
              <Power className={`w-20 h-20 transition-colors duration-500 ${isConnected ? 'text-minimal-success' : isTrialExpired || !hasAccess ? 'text-red-500' : 'app-text-muted'}`} />
              <div className="absolute bottom-10 font-black text-xs tracking-widest app-text-muted">
                {isConnected ? formatTime(uptime) : '00:00:00'}
              </div>
            </button>
          </motion.div>
        </div>

        {/* Status Text */}
        <div className="text-center z-10">
          <motion.h2 
            key={getStatusText()}
            initial={{ opacity: 0, y: 5 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-3xl font-black mb-2 app-text-bold tracking-tight"
          >
            {getStatusText()}
          </motion.h2>
          <p className="app-text-secondary text-sm font-medium">
            {getStatusSubtext()}
          </p>
        </div>
      </div>

      {/* Bottom Controls */}
      <div className="mt-auto space-y-6 z-10">
        <motion.div 
          onClick={() => onNavigate('servers')}
          whileHover={{ y: -2 }}
          className="app-card p-5 rounded-[2rem] flex items-center justify-between cursor-pointer transition-all shadow-[0_8px_30px_rgb(0,0,0,0.04)]"
        >
          <div className="flex items-center gap-4">
            <div className="w-11 h-11 app-bg rounded-2xl flex items-center justify-center app-text-secondary font-black text-xs">
              {selectedServer?.country.substring(0, 2).toUpperCase() || 'RU'}
            </div>
            <div>
              <p className="text-[10px] app-text-muted uppercase font-black tracking-[0.2em] mb-0.5">{t.selectedServer}</p>
              <p className="font-extrabold app-text text-sm">{selectedServer?.country || 'Russia'}, {selectedServer?.city || 'Moscow'}</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div className="flex flex-col items-end">
               <span className="text-minimal-success font-black text-[10px] uppercase">Stable</span>
               <span className="text-minimal-text-secondary font-mono text-xs">24ms</span>
            </div>
            <ChevronRight className="app-text-secondary w-5 h-5" />
          </div>
        </motion.div>

        {/* Floating Plus Button Corner */}
        <div className="flex justify-center relative">
          <button 
            onClick={() => setShowPlusMenu(true)}
            className="w-16 h-16 app-btn-primary rounded-3xl flex items-center justify-center shadow-xl shadow-minimal-accent/20 dark:shadow-none active:scale-95 transition-all"
          >
            <Plus className="w-8 h-8" />
          </button>
        </div>
      </div>

      {/* Plus Menu Modal */}
      <AnimatePresence>
        {showPlusMenu && (
          <>
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setShowPlusMenu(false)}
              className="absolute inset-0 bg-black/40 backdrop-blur-sm z-[100]"
            />
            <motion.div 
              initial={{ y: '100%' }}
              animate={{ y: 0 }}
              exit={{ y: '100%' }}
              transition={{ type: 'spring', damping: 25, stiffness: 200 }}
              className="absolute bottom-0 left-0 right-0 app-surface rounded-t-[3rem] p-8 z-[101] shadow-[0_-20px_60px_rgba(0,0,0,0.15)] dark:shadow-none"
            >
              <div className="w-12 h-1 app-border bg-minimal-border dark:bg-dark-border rounded-full mx-auto mb-8" />
              <div className="flex justify-between items-center mb-8">
                <div>
                   <h3 className="text-2xl font-black app-text-bold">Import Access</h3>
                   <p className="text-sm app-text-secondary">Choose your preferred method</p>
                </div>
                <button onClick={() => setShowPlusMenu(false)} className="p-2 hover:app-bg rounded-full"><X className="w-6 h-6 app-text-secondary" /></button>
              </div>

              <div className="grid grid-cols-2 gap-4">
                {plusMenuItems.map((item, i) => (
                  <button 
                    key={i} 
                    onClick={() => {
                      setShowPlusMenu(false);
                      onNavigate('import');
                    }}
                    className="flex flex-col items-center gap-4 p-6 app-bg rounded-[2rem] border-2 app-border hover:border-minimal-accent transition-all group active:scale-95"
                  >
                    <div className="w-14 h-14 app-surface rounded-[1.5rem] flex items-center justify-center shadow-sm group-hover:scale-110 transition-transform text-minimal-accent dark:text-dark-accent">
                      {item.icon}
                    </div>
                    <span className="font-bold app-text text-sm">{item.label}</span>
                  </button>
                ))}
              </div>

              <div className="mt-8 p-6 bg-minimal-accent/5 rounded-[2rem] flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <CreditCard className="w-5 h-5 text-minimal-accent dark:text-dark-accent" />
                  <span className="text-sm font-bold app-text">No access key?</span>
                </div>
                <button 
                  onClick={() => {
                    setShowPlusMenu(false);
                    onNavigate('subscription');
                  }}
                  className="text-xs font-black text-minimal-accent dark:text-dark-accent uppercase tracking-widest"
                >
                  Buy Premium
                </button>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
};

const ServersScreen = ({ servers, onBack }: any) => {
  return (
    <div className="h-full flex flex-col app-surface font-sans">
      <header className="p-6 flex items-center justify-between sticky top-0 app-surface z-20">
         <div className="flex items-center gap-4">
            <button 
              onClick={onBack} 
              className="w-12 h-12 app-bg border-2 app-border rounded-2xl flex items-center justify-center shadow-sm active:scale-90 transition-all app-text"
            >
              <ArrowRight className="rotate-180 app-text-secondary w-6 h-6" />
            </button>
            <h2 className="text-2xl font-black app-text-bold tracking-tighter uppercase">Servers</h2>
         </div>
         <div className="w-10 h-10 app-bg border-2 app-border rounded-xl flex items-center justify-center opacity-50"><Globe className="w-5 h-5 app-text-secondary" /></div>
      </header>
      
      <div className="flex-1 overflow-y-auto px-6 py-4 space-y-4">
        {servers.map((server: ServerNode) => (
          <motion.div 
            key={server.id} 
            whileTap={{ scale: 0.98 }}
            className="app-card p-5 rounded-[2rem] border-2 flex items-center justify-between hover:border-minimal-accent dark:hover:border-dark-accent transition-all cursor-pointer group shadow-sm"
          >
            <div className="flex items-center gap-5">
              <div className="w-14 h-14 bg-minimal-bg dark:bg-minimal-border/10 rounded-[1.5rem] flex items-center justify-center border-2 border-transparent group-hover:border-minimal-accent/20 app-text-secondary font-black text-xs uppercase tracking-widest">
                {server.country.substring(0, 2)}
              </div>
              <div>
                <p className="font-black app-text-bold tracking-tight uppercase text-sm">{server.country}</p>
                <p className="text-[10px] app-text-secondary uppercase font-black tracking-widest mt-0.5">{server.city}</p>
              </div>
            </div>
            <div className="flex flex-col items-end gap-1">
               <span className="text-minimal-success font-black text-[10px] uppercase tracking-widest">Premium</span>
               <div className="flex items-center gap-1.5">
                  <div className="w-1.5 h-1.5 bg-minimal-success rounded-full" />
                  <span className="app-text-secondary font-mono text-xs font-bold">24ms</span>
               </div>
            </div>
          </motion.div>
        ))}
      </div>
    </div>
  );
};

const ProfileScreen = ({ user, onBack, onNavigate }: any) => {
  return (
    <div className="h-full flex flex-col app-surface font-sans">
      <header className="p-6 flex items-center gap-4 sticky top-0 app-surface z-20">
        <button 
          onClick={onBack} 
          className="w-12 h-12 app-bg border-2 app-border rounded-2xl flex items-center justify-center shadow-sm active:scale-90 transition-all app-text"
        >
          <ArrowRight className="rotate-180 app-text-secondary w-6 h-6" />
        </button>
        <h2 className="text-2xl font-black app-text-bold tracking-tighter uppercase">Account</h2>
      </header>

      <div className="flex-1 overflow-y-auto px-6 pt-4 space-y-8 pb-10">
        <div className="app-card p-8 rounded-[3rem] relative overflow-hidden shadow-lg border-2">
          <div className="absolute top-6 right-6">
            <span className="text-[9px] px-3 py-1.5 bg-minimal-trial-bg dark:bg-dark-trial-bg text-minimal-trial-text dark:text-dark-trial-text rounded-full font-black uppercase tracking-[0.22em] border border-minimal-trial-text/10">{user?.planType || 'TRIAL'}</span>
          </div>
          <div className="w-20 h-20 app-bg rounded-[2rem] flex items-center justify-center mb-6 shadow-inner border-2 app-border">
             <User className="w-10 h-10 app-text-secondary opacity-50" />
          </div>
          <p className="app-text-muted text-[10px] font-black uppercase tracking-[0.3em] mb-1">User Identification</p>
          <h3 className="text-3xl font-black app-text-bold tracking-tighter mb-2">{user?.userNumber || 'ID #882412'}</h3>
          <p className="text-xs font-bold app-text-secondary tracking-tight opacity-70">v1-trial-user@swimvpn.ru</p>
        </div>

        <div className="space-y-4">
          <div className="flex flex-col gap-1">
            <p className="text-[10px] font-black app-text-muted uppercase tracking-[0.3em] ml-4 mb-3">Subscription Analytics</p>
            <div className="app-card rounded-[2.5rem] p-8 space-y-6 shadow-sm border-2">
              <div className="flex justify-between items-center">
                <span className="text-[11px] font-black uppercase tracking-widest app-text-secondary">Plan Expires</span>
                <span className="text-xs font-black text-red-500 uppercase tracking-widest">144 hours left</span>
              </div>
              <div className="w-full h-2.5 app-bg rounded-full overflow-hidden p-1 shadow-inner">
                <div className="h-full bg-minimal-accent dark:bg-dark-accent w-[80%] rounded-full shadow-sm" />
              </div>
              <div className="flex justify-between items-center pt-2 border-t app-border">
                <span className="text-[11px] font-black uppercase tracking-widest app-text-secondary">Active Connections</span>
                <span className="text-xs font-black app-text-bold">1 / 3 DEVICES</span>
              </div>
            </div>
          </div>

          <div className="space-y-2">
            <p className="text-[10px] font-black app-text-muted uppercase tracking-[0.3em] ml-4 mb-3">Management</p>
            <div className="app-card rounded-[2.5rem] overflow-hidden border-2 shadow-sm">
              {[
                { id: 'subscription', icon: <CreditCard className="w-5 h-5" />, label: 'Premium Subscription' },
                { id: 'import', icon: <Plus className="w-5 h-5" />, label: 'Activate Coupon' },
                { id: 'settings', icon: <Settings className="w-5 h-5" />, label: 'Account Settings' },
                { id: 'support', icon: <MessageSquare className="w-5 h-5" />, label: 'Help Center' }
              ].map((item, i) => (
                <div 
                  key={i} 
                  onClick={() => {
                    if (item.id === 'support') onNavigate('support');
                    if (item.id === 'import') onNavigate('import');
                    if (item.id === 'settings') onNavigate('settings');
                    if (item.id === 'subscription') onNavigate('subscription');
                  }}
                  className={`px-8 py-6 flex items-center justify-between hover:app-bg cursor-pointer transition-colors active:app-bg ${i !== 3 ? 'border-b-2 app-border' : ''}`}
                >
                  <div className="flex items-center gap-5 app-text-secondary font-black tracking-tight uppercase text-xs">
                    <div className="w-6 h-6 flex items-center justify-center opacity-70 group-hover:opacity-100 transition-opacity">
                      {item.icon}
                    </div>
                    <span className="font-black tracking-widest">{item.label}</span>
                  </div>
                  <ChevronRight className="w-5 h-5 app-text-muted" />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="p-6 pb-12 mt-auto">
        <Button variant="outline" className="w-full text-red-500 border-red-500/20 hover:bg-red-500 hover:text-white transition-all">SIGN OUT</Button>
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
    <div className="h-full flex flex-col app-surface font-sans">
      <header className="p-6 flex items-center justify-between sticky top-0 app-surface z-20">
         <div className="flex items-center gap-4">
            <button 
              onClick={onBack} 
              className="w-12 h-12 app-bg border-2 app-border rounded-2xl flex items-center justify-center shadow-sm active:scale-90 transition-all app-text"
            >
              <ArrowRight className="rotate-180 app-text-secondary w-6 h-6" />
            </button>
            <h2 className="text-2xl font-black app-text-bold tracking-tighter uppercase">Import</h2>
         </div>
         <div className="w-10 h-10 app-bg border-2 app-border rounded-xl flex items-center justify-center opacity-50"><Plus className="w-5 h-5 app-text-secondary" /></div>
      </header>

      <div className="flex-1 overflow-y-auto px-6 py-4 space-y-8 pb-10">
        <div className="space-y-4">
          <p className="text-[10px] font-black app-text-muted uppercase tracking-[0.3em] ml-4 mb-3">One-Tap Methods</p>
          <div className="grid grid-cols-2 gap-4">
               {[
                 { icon: <LinkIcon />, label: 'Paste Link' },
                 { icon: <QrCode />, label: 'Scan QR' },
                 { icon: <Clipboard />, label: 'Clipboard' },
                 { icon: <Plus />, label: 'Manual Key' }
               ].map((item, i) => (
                 <button 
                  key={i} 
                  onClick={() => handleImport(item.label)}
                  className="app-card p-6 rounded-[2.5rem] border-2 flex flex-col items-center gap-4 hover:border-minimal-accent dark:hover:border-dark-accent transition-all group shadow-sm active:scale-95"
                >
                   <div className="w-14 h-14 app-bg rounded-2xl flex items-center justify-center text-minimal-accent dark:text-dark-accent group-hover:scale-110 transition-transform">
                      {item.icon}
                   </div>
                   <span className="font-black text-[11px] app-text uppercase tracking-widest">{item.label}</span>
                 </button>
               ))}
           </div>
        </div>

        <div className="space-y-4">
          <p className="text-[10px] font-black app-text-muted uppercase tracking-[0.3em] ml-4 mb-3">Direct Input</p>
          <div className="app-card p-8 rounded-[3rem] border-2 shadow-lg space-y-6">
            <input 
              type="text" 
              placeholder="vless://access-key-here..." 
              value={code}
              onChange={(e) => setCode(e.target.value)}
              className="w-full app-bg border-2 app-border p-5 rounded-[1.5rem] outline-none focus:border-minimal-accent dark:focus:border-dark-accent transition-all text-sm font-bold placeholder:app-text-muted/50"
            />
            <Button onClick={() => handleImport('Manual Input')} className="w-full">ACTIVATE ACCESS</Button>
          </div>
        </div>

        <div className="p-10 app-bg rounded-[3rem] border-2 border-dashed app-border text-center">
           <SharkLogo className="w-16 h-16 text-minimal-accent dark:text-dark-accent mx-auto mb-6 opacity-30" />
           <h3 className="app-heading mb-3 text-2xl uppercase font-black">Need a key?</h3>
           <p className="text-sm app-text-secondary mb-8 leading-relaxed font-medium">Contact our authorized dealers or purchase directly via Telegram to receive your unique premium access key instantly.</p>
           <Button variant="outline" className="w-full">GO TO TELEGRAM</Button>
        </div>
      </div>
    </div>
  );
};

const SupportScreen = ({ onBack }: any) => {
  const faqs = [
    { q: "HOW TO CONNECT?", a: "Just tap the center power button on the home screen. Our engine will find the most secure server for you." },
    { q: "LOW CONNECTION SPEED", a: "Try switching to VLESS protocol or a different location in the servers list." },
    { q: "SUBSCRIPTION STATUS", a: "If your purchase isn't showing up, please send your User ID to our support bot on Telegram." },
    { q: "IS MY DATA SAFE?", a: "We use military-grade VLESS+Reality encryption that leaves zero digital footprint." }
  ];

  return (
    <div className="h-full flex flex-col app-surface font-sans">
      <header className="p-6 flex items-center gap-4 sticky top-0 app-surface z-20">
        <button 
          onClick={onBack} 
          className="w-12 h-12 app-bg border-2 app-border rounded-2xl flex items-center justify-center shadow-sm active:scale-90 transition-all app-text"
        >
          <ArrowRight className="rotate-180 app-text-secondary w-6 h-6" />
        </button>
        <h2 className="text-2xl font-black app-text-bold tracking-tighter uppercase">Support</h2>
      </header>

      <div className="flex-1 overflow-y-auto px-6 py-4 space-y-10 pb-10">
        <section className="space-y-4">
          <p className="text-[10px] font-black app-text-muted uppercase tracking-[0.3em] ml-4 mb-3">Knowledge Base</p>
          <div className="space-y-4">
            {faqs.map((faq, i) => (
              <div key={i} className="app-card p-6 rounded-[2.5rem] border-2 shadow-sm">
                <div className="font-black app-text-bold mb-3 text-[11px] uppercase tracking-widest flex items-center gap-2">
                   <div className="w-1.5 h-1.5 rounded-full bg-minimal-accent dark:bg-dark-accent" />
                   {faq.q}
                </div>
                <p className="text-xs app-text-secondary leading-relaxed font-medium opacity-80">{faq.a}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="space-y-4">
          <p className="text-[10px] font-black app-text-muted uppercase tracking-[0.3em] ml-4 mb-3">Direct Channels</p>
          <div className="grid grid-cols-1 gap-4">
             <button className="flex items-center gap-6 p-6 app-card rounded-[3rem] border-2 shadow-sm hover:shadow-lg transition-all group">
               <div className="w-14 h-14 app-bg rounded-2xl flex items-center justify-center text-minimal-accent dark:text-dark-accent group-hover:scale-110 transition-transform">
                 <MessageSquare className="w-7 h-7" />
               </div>
               <div className="text-left flex-1">
                 <p className="font-black app-text-bold text-sm tracking-tight uppercase">Telegram Official</p>
                 <p className="text-[11px] font-black app-text-secondary tracking-widest mt-0.5">@swimvpn_robot</p>
               </div>
               <ArrowRight className="w-5 h-5 app-text-muted" />
             </button>

             <button className="flex items-center gap-6 p-6 app-card rounded-[3rem] border-2 shadow-sm hover:shadow-lg transition-all group">
               <div className="w-14 h-14 app-bg rounded-2xl flex items-center justify-center text-red-500 group-hover:scale-110 transition-transform">
                 <HelpCircle className="w-7 h-7" />
               </div>
               <div className="text-left flex-1">
                 <p className="font-black app-text-bold text-sm tracking-tight uppercase">Email Support</p>
                 <p className="text-[11px] font-black app-text-secondary tracking-widest mt-0.5">support@swimvpn.ru</p>
               </div>
               <ArrowRight className="w-5 h-5 app-text-muted" />
             </button>
          </div>
        </section>

        <div className="mt-12 text-center">
          <p className="text-[9px] app-text-muted uppercase tracking-[0.5em] font-black mb-1">SWIMVPN+ CLIENT v2.4.5</p>
          <p className="text-[9px] app-text-muted font-bold opacity-50">STABLE PRODUCTION BUILD</p>
        </div>
      </div>
    </div>
  );
};

const SubscriptionScreen = ({ onBack }: any) => {
  const plans = [
    { 
      id: '1m', 
      name: 'PRO MONTHLY', 
      price: '$5.99', 
      period: '/ month',
      desc: 'Full premium network access',
    },
    { 
      id: '1y', 
      name: 'ANNUAL PASS', 
      price: '$39.99', 
      period: '/ year',
      desc: 'Maximum value for power users', 
      popular: true,
      badge: 'SAVE 45%'
    }
  ];

  const [selectedPlan, setSelectedPlan] = useState('1y');

  const features = [
    'UNLIMITED TRAFFIC SPEED',
    'EXCLUSIVE PRIVATE NODES',
    'MILITARY-GRADE ENCRYPTION',
    'UP TO 5 DEVICES SIMULTANEOUSLY',
    'PRIORITY SUPPORT ESCALATION'
  ];

  return (
    <div className="h-full flex flex-col app-surface font-sans relative transition-colors">
      <header className="p-6 flex items-center justify-between z-10 sticky top-0 app-surface">
        <button 
          onClick={onBack} 
          className="w-12 h-12 app-bg border-2 app-border rounded-2xl flex items-center justify-center shadow-sm active:scale-90 transition-all app-text"
        >
          <ArrowRight className="rotate-180 app-text-secondary w-6 h-6" />
        </button>
        <h2 className="text-[10px] font-black app-text-bold uppercase tracking-[0.4em]">Swim Premium</h2>
        <div className="w-12" />
      </header>

      <div className="flex-1 overflow-y-auto px-6 pt-4 space-y-12 pb-32">
        <div className="space-y-4">
          <h3 className="text-4xl font-black app-text-bold tracking-tighter leading-[1.1] uppercase">Unlimited<br />VPN Access</h3>
          <p className="app-text-secondary text-xs uppercase font-black tracking-widest opacity-60">Upgrade your experience today</p>
        </div>

        <div className="grid grid-cols-1 gap-5">
          {plans.map((plan) => (
            <motion.button 
              key={plan.id} 
              onClick={() => setSelectedPlan(plan.id)}
              whileTap={{ scale: 0.98 }}
              className={`group relative w-full p-8 rounded-[3rem] border-2 transition-all duration-500 text-left ${selectedPlan === plan.id ? 'border-minimal-accent dark:border-dark-accent app-surface shadow-2xl dark:shadow-none' : 'app-border app-bg opacity-50'}`}
            >
              {plan.badge && (
                <div className="absolute top-6 right-8 bg-minimal-accent dark:bg-dark-accent text-white dark:text-dark-bg text-[9px] font-black px-4 py-1.5 rounded-full uppercase tracking-widest shadow-lg">
                  {plan.badge}
                </div>
              )}
              
              <div className="flex flex-col gap-1">
                <span className={`text-[10px] font-black uppercase tracking-[0.2em] mb-1 ${selectedPlan === plan.id ? 'app-text-bold' : 'app-text-muted'}`}>{plan.name}</span>
                <div className="flex items-baseline gap-1">
                  <span className="text-4xl font-black app-text-bold tracking-tighter">{plan.price}</span>
                  <span className="text-xs app-text-muted font-black uppercase tracking-widest">{plan.period}</span>
                </div>
                <p className="text-[10px] app-text-secondary mt-3 font-black uppercase tracking-widest opacity-60">{plan.desc}</p>
              </div>
            </motion.button>
          ))}
        </div>

        <div className="space-y-6 px-4 pb-10">
          <h4 className="text-[10px] font-black app-text-muted uppercase tracking-[0.4em]">Core Privileges</h4>
          <ul className="space-y-5">
            {features.map((feature, i) => (
              <li key={i} className="flex items-center gap-5">
                <div className="w-5 h-5 rounded-full bg-minimal-accent dark:bg-dark-accent flex items-center justify-center shadow-lg dark:shadow-none">
                  <CheckCircle className="w-3 h-3 text-white dark:text-dark-bg" />
                </div>
                <span className="text-[11px] font-black uppercase tracking-widest app-text-secondary opacity-80">{feature}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className="p-6 pb-12 app-surface border-t-2 app-border z-20 shadow-2xl dark:shadow-none">
        <Button className="w-full">UPGRADE NOW</Button>
      </div>
    </div>
  );
};

const SettingsScreen = ({ config, onUpdateConfig, onBack }: any) => {
  const t = translations[config.language] || translations.EN;

  const [advancedMode, setAdvancedMode] = useState(false);

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

  const Section = ({ title, children, icon }: any) => (
    <section className="mb-12">
      <div className="flex items-center gap-3 mb-5 ml-4">
        {icon && <span className="app-text-muted opacity-50">{icon}</span>}
        <h3 className="text-[11px] font-black app-text-muted uppercase tracking-[0.4em]">{title}</h3>
      </div>
      <div className="app-card rounded-[3rem] overflow-hidden shadow-lg border-2">
        {children}
      </div>
    </section>
  );

  const SettingRow = ({ label, desc, children, noBorder, icon, isBeta }: any) => (
    <div className={`px-8 py-7 flex items-center justify-between ${!noBorder ? 'border-b-2 app-border' : ''} hover:bg-black/[0.02] dark:hover:bg-white/[0.02] transition-all group`}>
      <div className="flex items-center gap-6 flex-1 pr-6">
        {icon && <div className="w-12 h-12 rounded-2xl bg-minimal-bg dark:bg-dark-bg flex items-center justify-center transition-transform group-hover:scale-110 shadow-sm border app-border">{icon}</div>}
        <div>
          <div className="flex items-center gap-2">
            <p className="font-black app-text-bold text-sm uppercase tracking-tight">{label}</p>
            {isBeta && <span className="text-[8px] font-black bg-amber-100 dark:bg-amber-900/40 text-amber-600 dark:text-amber-400 px-2 py-1 rounded-full uppercase tracking-widest shadow-sm">BETA</span>}
          </div>
          {desc && <p className="text-[11px] font-black app-text-secondary uppercase tracking-widest mt-1.5 opacity-50 leading-tight">{desc}</p>}
        </div>
      </div>
      <div className="flex-shrink-0">{children}</div>
    </div>
  );

  const Toggle = ({ active, onToggle }: any) => (
    <button 
      onClick={onToggle}
      className={`w-14 h-8 rounded-full relative transition-all duration-500 shadow-inner ${active ? 'app-toggle-bg-active' : 'app-toggle-bg'}`}
    >
      <div className={`absolute top-1 w-6 h-6 rounded-full transition-all duration-500 ${active ? 'left-7 app-toggle-knob' : 'left-1 app-toggle-knob'}`} />
    </button>
  );

  const Select = ({ value, options, onChange }: any) => (
    <div className="relative flex items-center">
      <select 
        value={value} 
        onChange={(e) => onChange(e.target.value)}
        className="app-select pr-10 min-w-[100px] text-right"
      >
        {options.map((opt: any) => (
          <option key={typeof opt === 'string' ? opt : opt.value} value={typeof opt === 'string' ? opt : opt.value} className="app-text">
            {typeof opt === 'string' ? opt : opt.label}
          </option>
        ))}
      </select>
      <ChevronRight className="w-3 h-3 absolute right-4 rotate-90 app-text-bold pointer-events-none opacity-50" />
    </div>
  );

  return (
    <div className="h-full flex flex-col app-surface relative overflow-hidden transition-colors font-sans">
      <header className="p-6 flex items-center justify-between sticky top-0 app-surface z-30">
        <div className="flex items-center gap-4">
          <button 
            onClick={onBack} 
            className="w-12 h-12 app-bg border-2 app-border rounded-2xl flex items-center justify-center shadow-sm active:scale-90 transition-all app-text"
          >
            <ArrowRight className="rotate-180 app-text-secondary w-6 h-6" />
          </button>
          <h2 className="text-2xl font-black app-text-bold tracking-tighter uppercase">Settings</h2>
        </div>
        <button 
          onClick={resetSettings} 
          className="px-5 py-2.5 bg-red-500 text-white rounded-2xl text-[10px] font-black uppercase tracking-widest shadow-lg active:scale-95 transition-all"
        >
          Reset
        </button>
      </header>
      
      <div className="flex-1 overflow-y-auto px-6 py-8 pb-32">
        <Section title="Application" icon={<Globe className="w-4 h-4" />}>
          <SettingRow 
            label="Language" 
            desc="Interface translation"
            icon={<Languages className="w-5 h-5 app-text-bold" />}
          >
            <Select 
              value={config.language} 
              options={[{label: 'РУССКИЙ', value: 'RU'}, {label: 'ENGLISH', value: 'EN'}, {label: 'FRANÇAIS', value: 'FR'}]} 
              onChange={(val: string) => onUpdateConfig({...config, language: val})} 
            />
          </SettingRow>
          <SettingRow 
            label="Visual Theme" 
            desc="Appearance mode"
            noBorder
            icon={<Shield className="w-5 h-5 app-text-bold" />}
          >
            <Select 
              value={config.themeMode} 
              options={['Light', 'Dark', 'System']} 
              onChange={(val: string) => onUpdateConfig({...config, themeMode: val})} 
            />
          </SettingRow>
        </Section>

        <Section title="Connectivity" icon={<Zap className="w-4 h-4" />}>
          <SettingRow 
            label="Routing" 
            desc="Tunneling logic"
            icon={<Network className="w-5 h-5 app-text-bold" />}
          >
             <Select 
              value={config.vpnMode} 
              options={['TUNNEL', 'PROXY']} 
              onChange={(val: string) => onUpdateConfig({...config, vpnMode: val})} 
            />
          </SettingRow>
          <SettingRow 
            label="Auto Start" 
            desc="Boot connection"
            icon={<Power className="w-5 h-5 app-text-bold" />}
          >
            <Toggle active={config.autoConnect} onToggle={() => onUpdateConfig({...config, autoConnect: !config.autoConnect})} />
          </SettingRow>
          <SettingRow 
            label="Secured Kill" 
            desc="Drop all traffic" 
            noBorder
            icon={<Shield className="w-5 h-5 app-text-bold" />}
          >
            <Toggle active={config.killSwitch} onToggle={() => onUpdateConfig({...config, killSwitch: !config.killSwitch})} />
          </SettingRow>
        </Section>

        <Section title="Technical" icon={<Cpu className="w-4 h-4" />}>
          <SettingRow 
            label="Expert Mode" 
            desc="System fine-tuning" 
            icon={<Settings className="w-5 h-5 app-text-bold" />}
          >
            <Toggle active={advancedMode} onToggle={() => setAdvancedMode(!advancedMode)} />
          </SettingRow>
          
          <AnimatePresence>
            {advancedMode && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                className="overflow-hidden border-t-2 app-border"
              >
                <SettingRow 
                  label="Protocol" 
                  desc="Engine selection"
                  icon={<Globe className="w-5 h-5 app-text-bold" />}
                >
                  <Select 
                    value={config.protocol} 
                    options={['VLESS', 'REALITY', 'SSH']} 
                    onChange={(val: string) => onUpdateConfig({...config, protocol: val})} 
                  />
                </SettingRow>
                <SettingRow 
                  label="DNS" 
                  desc="Address resolution" 
                  noBorder
                  icon={<Shield className="w-5 h-5 app-text-bold" />}
                >
                   <Select 
                    value={config.dnsMode} 
                    options={['SYSTEM', 'GOOGLE', 'LOCAL']} 
                    onChange={(val: string) => onUpdateConfig({...config, dnsMode: val})} 
                  />
                </SettingRow>
              </motion.div>
            )}
          </AnimatePresence>
        </Section>
      </div>

      <div className="p-6 pb-12 app-surface border-t-2 app-border z-30 shadow-2xl dark:shadow-none">
        <Button onClick={saveSettings} className="w-full">SAVE ALL PREFERENCES</Button>
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
    const updateTheme = () => {
      const isDark = config.themeMode === 'Dark' || (config.themeMode === 'System' && window.matchMedia('(prefers-color-scheme: dark)').matches);
      document.documentElement.classList.toggle('dark', isDark);
    };

    updateTheme();

    if (config.themeMode === 'System') {
      const mediaWatcher = window.matchMedia('(prefers-color-scheme: dark)');
      mediaWatcher.addEventListener('change', updateTheme);
      return () => mediaWatcher.removeEventListener('change', updateTheme);
    }
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

const LoadingScreen = () => (
  <div className="h-full w-full app-surface flex flex-col items-center justify-center p-8 transition-colors duration-500">
    <motion.div 
      animate={{ scale: [1, 1.05, 1] }}
      transition={{ repeat: Infinity, duration: 2.5 }}
      className="w-32 h-32 app-bg rounded-[3rem] border-4 app-border flex items-center justify-center shadow-2xl mb-12 shadow-minimal-accent/20 dark:shadow-none"
    >
      <SharkLogo className="w-20 h-20 text-minimal-accent dark:text-dark-accent" />
    </motion.div>
    <div className="flex flex-col items-center gap-3">
       <p className="app-text-bold font-black tracking-[0.5em] uppercase text-sm">SWIMVPN+</p>
       <p className="text-[10px] app-text-secondary font-black uppercase tracking-[0.2em] opacity-50">Secure Protection Layer</p>
    </div>
    <div className="mt-16 flex gap-2">
      <div className="w-2 h-2 bg-minimal-accent dark:bg-dark-accent rounded-full animate-bounce" style={{ animationDelay: '0s' }} />
      <div className="w-2 h-2 bg-minimal-accent dark:bg-dark-accent rounded-full animate-bounce" style={{ animationDelay: '0.15s' }} />
      <div className="w-2 h-2 bg-minimal-accent dark:bg-dark-accent rounded-full animate-bounce" style={{ animationDelay: '0.3s' }} />
    </div>
  </div>
);

  if (loading) return <LoadingScreen />;

  return (
    <div className="h-full w-full app-bg flex items-center justify-center font-sans overflow-hidden transition-all duration-300">
      {/* Phone Frame */}
      <div className="relative w-full max-w-[390px] aspect-[9/19.5] max-h-[844px] app-surface rounded-[3.5rem] border-[10px] app-border shadow-[0_20px_60px_rgba(0,0,0,0.1)] dark:shadow-none overflow-hidden scale-90 sm:scale-100">

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
        <div className="p-8 app-card rounded-[2.5rem] shadow-xl">
          <h2 className="text-2xl font-black mb-2 app-text-bold tracking-tighter uppercase leading-tight">SWIMVPN+<br />DEVELOPER INFO</h2>
          <p className="app-text-secondary text-sm mb-10 leading-relaxed font-medium">
            Midnight Blue & Electric Blue Architecture. 
            Deeply themed minimalism optimized for high-performance VPN clients.
          </p>
          <div className="space-y-6">
            <div className="flex items-center gap-5">
              <div className="w-12 h-12 app-bg rounded-2xl flex items-center justify-center text-minimal-accent dark:text-dark-accent border-2 app-border shadow-sm"><Globe className="w-6 h-6"/></div>
              <div>
                <p className="font-black text-sm app-text-bold uppercase tracking-tight">Unified Engine</p>
                <p className="text-[10px] app-text-muted font-black uppercase tracking-widest mt-0.5">Real-time adaptive logic</p>
              </div>
            </div>
            <div className="flex items-center gap-5">
              <div className="w-12 h-12 app-bg rounded-2xl flex items-center justify-center text-minimal-accent dark:text-dark-accent border-2 app-border shadow-sm"><Shield className="w-6 h-6"/></div>
              <div>
                <p className="font-black text-sm app-text-bold uppercase tracking-tight">Stealth Mode</p>
                <p className="text-[10px] app-text-muted font-black uppercase tracking-widest mt-0.5">Traffic obsfuscation v2</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
