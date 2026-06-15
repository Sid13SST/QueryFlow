import { useState } from 'react';
import axiosClient from './api/axiosClient';

function App() {
  const [healthInfo, setHealthInfo] = useState(null);
  const [errorInfo, setErrorInfo] = useState(null);
  const [isLoading, setIsLoading] = useState(false);

  const checkHealth = async () => {
    setIsLoading(true);
    setHealthInfo(null);
    setErrorInfo(null);
    try {
      const response = await axiosClient.get('/health');
      setHealthInfo(response.data);
    } catch (error) {
      setErrorInfo(error.message || 'Failed to reach backend server');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between selection:bg-indigo-500/30 relative overflow-x-hidden">
      {/* Decorative background gradients */}
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none z-0">
        <div className="absolute top-[-20%] left-[-10%] w-[60%] h-[60%] rounded-full bg-indigo-900/20 blur-[120px]" />
        <div className="absolute bottom-[-20%] right-[-10%] w-[60%] h-[60%] rounded-full bg-blue-900/20 blur-[120px]" />
      </div>

      {/* Header */}
      <header className="w-full max-w-7xl mx-auto px-6 py-6 flex items-center justify-between border-b border-slate-900 z-10">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-blue-500 flex items-center justify-center font-bold text-xl tracking-wider text-white shadow-lg shadow-indigo-500/20">
            Q
          </div>
          <span className="font-semibold text-xl tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-white to-slate-400">
            QueryFlow
          </span>
        </div>
        <div className="flex items-center gap-4 text-sm text-slate-400">
          <span className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            Phase 0 Foundation
          </span>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 flex flex-col items-center justify-center px-6 py-12 z-10">
        <div className="w-full max-w-2xl text-center space-y-8">
          {/* Badge */}
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 text-xs font-medium tracking-wide">
            Distributed Search Typeahead System
          </div>

          {/* Heading */}
          <div className="space-y-4">
            <h1 className="text-5xl md:text-6xl font-extrabold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-white via-slate-100 to-slate-500">
              QueryFlow
            </h1>
            <p className="text-lg md:text-xl text-slate-400 max-w-xl mx-auto font-light leading-relaxed">
              High-performance query auto-suggestions backed by consistent hashing, caching, and batch write pipeline optimization.
            </p>
          </div>

          {/* Action Box */}
          <div className="p-8 rounded-2xl bg-slate-900/40 border border-slate-800/80 backdrop-blur-xl shadow-2xl space-y-6 max-w-md mx-auto">
            <h3 className="text-md font-medium text-slate-300">Architecture Foundation Status</h3>
            
            <button
              id="check-health-btn"
              onClick={checkHealth}
              disabled={isLoading}
              className={`w-full py-3.5 px-6 rounded-xl font-medium tracking-wide transition-all duration-300 flex items-center justify-center gap-2 shadow-lg ${
                isLoading
                  ? 'bg-slate-800 text-slate-500 cursor-not-allowed border border-slate-700/50'
                  : 'bg-gradient-to-r from-indigo-600 to-blue-500 hover:from-indigo-500 hover:to-blue-400 text-white shadow-indigo-500/25 hover:shadow-indigo-500/35 active:scale-[0.98]'
              }`}
            >
              {isLoading ? (
                <>
                  <svg className="animate-spin h-5 w-5 text-indigo-400" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                  </svg>
                  Checking backend connection...
                </>
              ) : (
                'Check Backend Health'
              )}
            </button>

            {/* Health Info Display */}
            {healthInfo && (
              <div className="p-4 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 space-y-1.5 text-left text-sm transition-all duration-300 animate-fadeIn">
                <div className="flex items-center gap-2 font-semibold">
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-ping" />
                  <span>Backend Status: {healthInfo.status}</span>
                </div>
                <div className="text-xs text-emerald-500/70">
                  Service Name: {healthInfo.service || 'QueryFlow'}
                </div>
              </div>
            )}

            {/* Error Info Display */}
            {errorInfo && (
              <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 space-y-1 text-left text-sm transition-all duration-300 animate-fadeIn">
                <div className="font-semibold flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                  <span>Connection Failed</span>
                </div>
                <p className="text-xs text-rose-500/70 leading-relaxed font-mono mt-1 break-all">
                  Error Details: {errorInfo}
                </p>
              </div>
            )}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full max-w-7xl mx-auto px-6 py-8 border-t border-slate-900 flex flex-col md:flex-row justify-between items-center gap-4 text-sm text-slate-500 z-10">
        <p>&copy; {new Date().getFullYear()} QueryFlow. All rights reserved.</p>
        <div className="flex gap-6">
          <span className="hover:text-indigo-400 transition-colors cursor-pointer">Distributed Systems</span>
          <span className="hover:text-indigo-400 transition-colors cursor-pointer">Consistent Hashing</span>
          <span className="hover:text-indigo-400 transition-colors cursor-pointer">Spring & React</span>
        </div>
      </footer>
    </div>
  );
}

export default App;
