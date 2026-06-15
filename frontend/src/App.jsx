import { useState, useEffect } from 'react';
import axiosClient from './api/axiosClient';
import SearchBox from './components/SearchBox';
import logo from './assets/logo.svg';

function App() {
  const [healthInfo, setHealthInfo] = useState(null);
  const [healthError, setHealthError] = useState(null);
  const [isHealthLoading, setIsHealthLoading] = useState(false);

  const [statsInfo, setStatsInfo] = useState(null);
  const [statsError, setStatsError] = useState(null);
  const [isStatsLoading, setIsStatsLoading] = useState(false);

  const [cacheInfo, setCacheInfo] = useState(null);
  const [cacheError, setCacheError] = useState(null);
  const [isCacheLoading, setIsCacheLoading] = useState(false);

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

  const fetchCacheStats = async () => {
    setIsCacheLoading(true);
    setCacheInfo(null);
    setCacheError(null);
    try {
      const response = await axiosClient.get('/cache/stats');
      setCacheInfo(response.data);
    } catch (error) {
      setCacheError(error.message || 'Failed to fetch cache stats');
    } finally {
      setIsCacheLoading(false);
    }
  };

  // Fetch stats on load
  useEffect(() => {
    fetchStats();
    fetchCacheStats();
  }, []);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between selection:bg-indigo-500/30 relative overflow-x-hidden">
      {/* Decorative background gradients */}
      <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none z-0">
        <div className="absolute top-[-20%] left-[-10%] w-[60%] h-[60%] rounded-full bg-indigo-900/15 blur-[120px]" />
        <div className="absolute bottom-[-20%] right-[-10%] w-[60%] h-[60%] rounded-full bg-blue-900/15 blur-[120px]" />
      </div>

      {/* Header */}
      <header className="w-full max-w-7xl mx-auto px-6 py-6 flex items-center justify-between border-b border-slate-900/80 z-10 backdrop-blur-sm">
        <div className="flex items-center gap-3">
          <img 
            src={logo} 
            alt="QueryFlow Logo" 
            className="w-10 h-10 filter drop-shadow-[0_0_8px_rgba(99,102,241,0.4)]" 
          />
          <span className="font-semibold text-xl tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-white to-slate-400">
            QueryFlow
          </span>
        </div>
        <div className="flex items-center gap-4 text-sm text-slate-400">
          <span className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-slate-900 border border-slate-800 text-xs">
            <span className="w-2 h-2 rounded-full bg-indigo-500 animate-pulse" />
            Phase 4 Caching
          </span>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 flex flex-col items-center justify-center px-6 py-16 z-10">
        <div className="w-full max-w-4xl text-center space-y-12">
          
          {/* Logo, Badge and Heading Section */}
          <div className="space-y-6">
            {/* Animated Large Logo */}
            <div className="flex justify-center">
              <div className="relative group cursor-pointer">
                <div className="absolute inset-0 bg-gradient-to-tr from-indigo-500 via-blue-500 to-emerald-500 rounded-full blur-[40px] opacity-25 group-hover:opacity-50 transition-opacity duration-700" />
                <img 
                  src={logo} 
                  alt="QueryFlow Logo" 
                  className="w-32 h-32 relative z-10 transition-transform duration-700 ease-out group-hover:scale-110 drop-shadow-[0_10px_20px_rgba(0,0,0,0.5)]" 
                />
              </div>
            </div>

            {/* Badge */}
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 text-xs font-semibold tracking-wide shadow-inner">
              Distributed Search Typeahead System
            </div>

            {/* Main Title */}
            <div className="space-y-4">
              <h1 className="text-5xl md:text-6xl font-extrabold tracking-tight bg-clip-text text-transparent bg-gradient-to-b from-white via-slate-100 to-slate-500 drop-shadow-sm">
                QueryFlow
              </h1>
              <p className="text-lg md:text-xl text-slate-400 max-w-xl mx-auto font-light leading-relaxed">
                High-performance query auto-suggestions backed by consistent hashing, caching, and batch write pipeline optimization.
              </p>
            </div>
          </div>

          {/* Search Box Component */}
          <div className="py-2 max-w-xl mx-auto w-full">
            <SearchBox />
          </div>

          {/* Action Cards Container */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 max-w-5xl mx-auto w-full relative pt-6">
            
            {/* Health Card */}
            <div className="p-8 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl flex flex-col justify-between text-left hover:border-slate-700/60 transition-colors duration-300">
              <div className="space-y-2">
                <h3 className="text-lg font-bold text-slate-200">System Health</h3>
                <p className="text-sm text-slate-400 font-light">Verify connection connectivity to QueryFlow's Java backend endpoints.</p>
              </div>

              <div className="space-y-4 mt-6">
                <button
                  id="check-health-btn"
                  onClick={checkHealth}
                  disabled={isHealthLoading}
                  className={`w-full py-3.5 px-6 rounded-xl font-medium tracking-wide transition-all duration-300 flex items-center justify-center gap-2 shadow-lg cursor-pointer ${
                    isHealthLoading
                      ? 'bg-slate-800 text-slate-500 cursor-not-allowed border border-slate-700/50'
                      : 'bg-gradient-to-r from-indigo-600 to-blue-500 hover:from-indigo-500 hover:to-blue-400 text-white shadow-indigo-500/15 hover:shadow-indigo-500/30 active:scale-[0.98]'
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
                  <div className="p-4 rounded-xl bg-emerald-500/5 border border-emerald-500/10 text-emerald-400 space-y-1.5 text-left text-sm transition-all duration-300 animate-fadeIn">
                    <div className="flex items-center gap-2 font-semibold">
                      <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
                      <span>Backend Status: {healthInfo.status}</span>
                    </div>
                    <div className="text-xs text-emerald-500/60">
                      Service Name: {healthInfo.service || 'QueryFlow'}
                    </div>
                  </div>
                )}

                {/* Error Info Display */}
                {healthError && (
                  <div className="p-4 rounded-xl bg-rose-500/5 border border-rose-500/10 text-rose-400 space-y-1 text-left text-sm transition-all duration-300 animate-fadeIn">
                    <div className="font-semibold flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-rose-500" />
                      <span>Connection Failed</span>
                    </div>
                    <p className="text-xs text-rose-500/60 leading-relaxed font-mono mt-1 break-all">
                      Error Details: {healthError}
                    </p>
                  </div>
                )}
              </div>
            </div>

            {/* Stats Card */}
            <div className="p-8 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl flex flex-col justify-between text-left hover:border-slate-700/60 transition-colors duration-300">
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <h3 className="text-lg font-bold text-slate-200">Dataset Statistics</h3>
                  <button 
                    onClick={fetchStats}
                    disabled={isStatsLoading}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors cursor-pointer"
                    title="Refresh stats"
                  >
                    <svg className={`h-4.5 w-4.5 ${isStatsLoading ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.21 7.89M9 11l3-3 3 3m-3-3v12" />
                    </svg>
                  </button>
                </div>
                <p className="text-sm text-slate-400 font-light">Total search queries parsed and loaded into the PostgreSQL storage engine.</p>
              </div>

              <div className="space-y-4 mt-6">
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
                    <div className="p-4 rounded-2xl bg-slate-950/80 border border-slate-800/80 flex items-center justify-between shadow-inner">
                      <div>
                        <span className="text-[10px] text-slate-500 block uppercase tracking-wider font-semibold">Total Queries</span>
                        <span className="text-3xl font-black text-indigo-400 mt-1 block">
                          {statsInfo.totalQueries.toLocaleString()}
                        </span>
                      </div>
                      <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 shadow-md">
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
                        </svg>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-slate-400 px-1 pt-1">
                      <span className={`w-2 h-2 rounded-full ${statsInfo.datasetLoaded ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'}`} />
                      <span>{statsInfo.datasetLoaded ? 'CSV dataset loaded successfully' : 'No queries loaded yet'}</span>
                    </div>
                  </div>
                ) : statsError ? (
                  <div className="p-4 rounded-xl bg-rose-500/5 border border-rose-500/10 text-rose-400 text-left text-sm">
                    <div className="font-semibold text-xs uppercase tracking-wide">Failed to fetch stats</div>
                    <p className="text-xs text-rose-500/60 mt-1.5 leading-relaxed">{statsError}</p>
                  </div>
                ) : (
                  <div className="h-24 flex items-center justify-center text-slate-500 text-sm">
                    No statistics data available.
                  </div>
                )}
              </div>
            </div>

            {/* Cache Stats Card */}
            <div className="p-8 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl flex flex-col justify-between text-left hover:border-slate-700/60 transition-colors duration-300">
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <h3 className="text-lg font-bold text-slate-200">Cache Performance</h3>
                  <button 
                    onClick={fetchCacheStats}
                    disabled={isCacheLoading}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors cursor-pointer"
                    title="Refresh cache stats"
                  >
                    <svg className={`h-4.5 w-4.5 ${isCacheLoading ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.21 7.89M9 11l3-3 3 3m-3-3v12" />
                    </svg>
                  </button>
                </div>
                <p className="text-sm text-slate-400 font-light">Real-time Redis cache lookup performance statistics and hit rate tracking.</p>
              </div>

              <div className="space-y-4 mt-6">
                {isCacheLoading ? (
                  <div className="h-24 flex items-center justify-center text-slate-500 text-sm">
                    <svg className="animate-spin h-5 w-5 text-indigo-400 mr-2" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    Fetching stats...
                  </div>
                ) : cacheInfo ? (
                  <div className="space-y-3">
                    <div className="p-4 rounded-2xl bg-slate-950/80 border border-slate-800/80 flex items-center justify-between shadow-inner">
                      <div>
                        <span className="text-[10px] text-slate-500 block uppercase tracking-wider font-semibold">Hit Rate</span>
                        <span className="text-3xl font-black text-indigo-400 mt-1 block">
                          {cacheInfo.hitRate}%
                        </span>
                      </div>
                      <div className="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 shadow-md">
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                        </svg>
                      </div>
                    </div>
                    
                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <div className="p-3.5 rounded-xl bg-slate-950/45 border border-slate-800/80 flex justify-between items-center">
                        <span className="text-slate-500">Hits</span>
                        <span className="font-semibold text-emerald-400 font-mono">{cacheInfo.hits}</span>
                      </div>
                      <div className="p-3.5 rounded-xl bg-slate-950/45 border border-slate-800/80 flex justify-between items-center">
                        <span className="text-slate-500">Misses</span>
                        <span className="font-semibold text-rose-400 font-mono">{cacheInfo.misses}</span>
                      </div>
                    </div>
                  </div>
                ) : cacheError ? (
                  <div className="p-4 rounded-xl bg-rose-500/5 border border-rose-500/10 text-rose-400 text-left text-sm">
                    <div className="font-semibold text-xs uppercase tracking-wide">Failed to fetch stats</div>
                    <p className="text-xs text-rose-500/60 mt-1.5 leading-relaxed">{cacheError}</p>
                  </div>
                ) : (
                  <div className="h-24 flex items-center justify-center text-slate-500 text-sm">
                    No cache statistics available.
                  </div>
                )}
              </div>
            </div>

          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full max-w-7xl mx-auto px-6 py-8 border-t border-slate-900/60 flex flex-col md:flex-row justify-between items-center gap-4 text-sm text-slate-500 z-10">
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
