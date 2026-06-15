import { useState, useEffect } from 'react';
import axiosClient from './api/axiosClient';

function App() {
  const [healthInfo, setHealthInfo] = useState(null);
  const [healthError, setHealthError] = useState(null);
  const [isHealthLoading, setIsHealthLoading] = useState(false);

  const [statsInfo, setStatsInfo] = useState(null);
  const [statsError, setStatsError] = useState(null);
  const [isStatsLoading, setIsStatsLoading] = useState(false);

  const checkHealth = async () => {
    setIsHealthLoading(true);
    setHealthInfo(null);
    setHealthError(null);
    try {
      const response = await axiosClient.get('/health');
      setHealthInfo(response.data);
    } catch (error) {
      setHealthError(error.message || 'Failed to reach backend server');
    } finally {
      setIsHealthLoading(false);
    }
  };

  const fetchStats = async () => {
    setIsStatsLoading(true);
    setStatsInfo(null);
    setStatsError(null);
    try {
      const response = await axiosClient.get('/dataset/stats');
      setStatsInfo(response.data);
    } catch (error) {
      setStatsError(error.message || 'Failed to fetch dataset stats');
    } finally {
      setIsStatsLoading(false);
    }
  };

  // Fetch stats on load
  useEffect(() => {
    fetchStats();
  }, []);

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
            <span className="w-2 h-2 rounded-full bg-indigo-500 animate-pulse" />
            Phase 1 Storage
          </span>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 flex flex-col items-center justify-center px-6 py-12 z-10">
        <div className="w-full max-w-4xl text-center space-y-8">
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

          {/* Action Cards Container */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-3xl mx-auto w-full relative">
            
            {/* Health Card */}
            <div className="p-8 rounded-2xl bg-slate-900/40 border border-slate-800/80 backdrop-blur-xl shadow-2xl space-y-6 flex flex-col justify-between text-left">
              <div className="space-y-2">
                <h3 className="text-lg font-semibold text-slate-200">System Health</h3>
                <p className="text-sm text-slate-400">Verify connection connectivity to QueryFlow's Java backend endpoints.</p>
              </div>

              <div className="space-y-4">
                <button
                  id="check-health-btn"
                  onClick={checkHealth}
                  disabled={isHealthLoading}
                  className={`w-full py-3.5 px-6 rounded-xl font-medium tracking-wide transition-all duration-300 flex items-center justify-center gap-2 shadow-lg cursor-pointer ${
                    isHealthLoading
                      ? 'bg-slate-800 text-slate-500 cursor-not-allowed border border-slate-700/50'
                      : 'bg-gradient-to-r from-indigo-600 to-blue-500 hover:from-indigo-500 hover:to-blue-400 text-white shadow-indigo-500/25 hover:shadow-indigo-500/35 active:scale-[0.98]'
                  }`}
                >
                  {isHealthLoading ? (
                    <>
                      <svg className="animate-spin h-5 w-5 text-indigo-400" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                      </svg>
                      Checking backend...
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
                {healthError && (
                  <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 space-y-1 text-left text-sm transition-all duration-300 animate-fadeIn">
                    <div className="font-semibold flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                      <span>Connection Failed</span>
                    </div>
                    <p className="text-xs text-rose-500/70 leading-relaxed font-mono mt-1 break-all">
                      Error Details: {healthError}
                    </p>
                  </div>
                )}
              </div>
            </div>

            {/* Stats Card */}
            <div className="p-8 rounded-2xl bg-slate-900/40 border border-slate-800/80 backdrop-blur-xl shadow-2xl space-y-6 flex flex-col justify-between text-left">
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <h3 className="text-lg font-semibold text-slate-200">Dataset Statistics</h3>
                  <button 
                    onClick={fetchStats}
                    disabled={isStatsLoading}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-850 transition-colors cursor-pointer"
                    title="Refresh stats"
                  >
                    <svg className={`h-4.5 w-4.5 ${isStatsLoading ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.21 7.89M9 11l3-3 3 3m-3-3v12" />
                    </svg>
                  </button>
                </div>
                <p className="text-sm text-slate-400">Total search queries parsed and loaded into the PostgreSQL storage engine.</p>
              </div>

              <div className="space-y-4">
                {isStatsLoading ? (
                  <div className="h-24 flex items-center justify-center text-slate-500 text-sm">
                    <svg className="animate-spin h-5 w-5 text-indigo-400 mr-2" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    Fetching statistics...
                  </div>
                ) : statsInfo ? (
                  <div className="space-y-3">
                    <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 flex items-center justify-between">
                      <div>
                        <span className="text-xs text-slate-500 block uppercase tracking-wider font-semibold">Total Queries</span>
                        <span className="text-3xl font-extrabold text-indigo-400 mt-1 block">
                          {statsInfo.totalQueries.toLocaleString()}
                        </span>
                      </div>
                      <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
                        </svg>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-slate-400 px-1">
                      <span className={`w-2 h-2 rounded-full ${statsInfo.datasetLoaded ? 'bg-emerald-500' : 'bg-amber-500'}`} />
                      <span>{statsInfo.datasetLoaded ? 'CSV dataset loaded successfully' : 'No queries loaded yet'}</span>
                    </div>
                  </div>
                ) : statsError ? (
                  <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 text-left text-sm">
                    <div className="font-semibold">Failed to fetch stats</div>
                    <p className="text-xs text-rose-500/70 mt-1 leading-relaxed">{statsError}</p>
                  </div>
                ) : (
                  <div className="h-24 flex items-center justify-center text-slate-500 text-sm">
                    No statistics data available.
                  </div>
                )}
              </div>
            </div>

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
