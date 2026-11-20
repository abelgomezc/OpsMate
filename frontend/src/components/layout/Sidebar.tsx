import { Link } from 'react-router-dom';

export function Sidebar() {
  return (
    <aside className="fixed inset-y-0 left-0 z-50 w-64 border-r border-gray-200 bg-white">
      <div className="flex h-16 items-center justify-center gap-2 border-b border-gray-200 bg-gray-50">
        <span className="text-blue-600">Ω</span>
        <span className="font-bold text-gray-900 tracking-tight">OpsMate</span>
      </div>
      <nav className="mt-6 space-y-1 px-3">
        {[
          { to: '/casos', label: 'Casos', icon: '📋' },
          { to: '/aprobaciones', label: 'Aprobaciones', icon: '🛡️' },
          { to: '/sistemas', label: 'Sistemas', icon: '🖥️' },
          { to: '/panel-fallos', label: 'Panel de fallos', icon: '⚡' },
        ].map((item) => (
          <Link
            key={item.to}
            to={item.to}
            className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-gray-700 hover:bg-blue-50 hover:text-blue-600 transition-colors"
          >
            <span className="text-lg">{item.icon}</span>
            {item.label}
          </Link>
        ))}
      </nav>
      <div className="absolute bottom-0 left-0 right-0 p-4 border-t border-gray-200 bg-gray-50">
        <p className="text-xs text-gray-500 text-center">v1.0.0 · Fase 8</p>
      </div>
    </aside>
  );
}

export function TopBar({ onNewCase }: { onNewCase: () => void }) {
  return (
    <header className="sticky top-0 z-40 flex h-16 items-center justify-between gap-4 px-4 border-b border-gray-200 bg-white backdrop-blur bg-white/90">
      <div className="flex items-center gap-4">
        <h1 className="text-sm font-semibold text-gray-900">Casos de operación</h1>
      </div>
      <div className="flex items-center gap-3">
        <button
          onClick={onNewCase}
          className="btn btn-primary"
        >
          Nuevo caso
        </button>
      </div>
    </header>
  );
}
