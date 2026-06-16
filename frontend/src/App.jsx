import { useState, useEffect } from 'react';
import axiosClient from './api/axiosClient';
import SearchBox from './components/SearchBox';
import TrendingSearches from './components/TrendingSearches';
import DeveloperDashboard from './components/DeveloperDashboard';
import logo from './assets/logo.svg';

const vNodeCount = 30;
const virtualNodes = Array.from({ length: vNodeCount }).map((_, i) => {
  const angle = (i * 360) / vNodeCount;
  const rad = (angle * Math.PI) / 180;
  const radius = 70;
  const x = 100 + radius * Math.cos(rad);
  const y = 100 + radius * Math.sin(rad);
  
  let nodeId = 'redis-node-1';
  let color = '#6366f1'; // Indigo
  let hoverColor = '#818cf8';
  if (i % 3 === 1) {
    nodeId = 'redis-node-2';
    color = '#10b981'; // Emerald
    hoverColor = '#34d399';
  } else if (i % 3 === 2) {
    nodeId = 'redis-node-3';
    color = '#06b6d4'; // Cyan
    hoverColor = '#22d3ee';
  }
  return { id: i, angle, x, y, nodeId, color, hoverColor };
});

const physicalNodes = [
  {
    id: 'redis-node-1',
    label: 'Node 1',
    x: 170,
    y: 100,
    color: '#6366f1',
    pulseColor: 'rgba(99, 102, 241, 0.4)'
  },
  {
    id: 'redis-node-2',
    label: 'Node 2',
    x: 65,
    y: 160.6,
    color: '#10b981',
    pulseColor: 'rgba(16, 185, 129, 0.4)'
  },
  {
    id: 'redis-node-3',
    label: 'Node 3',
    x: 65,
    y: 39.4,
    color: '#06b6d4',
    pulseColor: 'rgba(6, 182, 212, 0.4)'
  }
];

function App() {
  const [healthInfo, setHealthInfo] = useState(null);
  const [healthError, setHealthError] = useState(null);
  const [isHealthLoading, setIsHealthLoading] = useState(false);

  const [statsInfo, setStatsInfo] = useState(null);
  const [statsError, setStatsError] = useState(null);
  const [isStatsLoading, setIsStatsLoading] = useState(false);

  const [cacheInfo, setCacheInfo] = useState(null);
  const [ringInfo, setRingInfo] = useState(null);
  const [distInfo, setDistInfo] = useState(null);
  const [cacheError, setCacheError] = useState(null);
  const [isCacheLoading, setIsCacheLoading] = useState(false);

  const [autoRefresh, setAutoRefresh] = useState(true);
  const [hoveredNodeId, setHoveredNodeId] = useState(null);
  const [searchTrigger, setSearchTrigger] = useState(null);
  const [batchInfo, setBatchInfo] = useState(null);
  const [invalidationInfo, setInvalidationInfo] = useState(null);
  const [metricsInfo, setMetricsInfo] = useState(null);
  const [latencyInfo, setLatencyInfo] = useState(null);
  const [benchmarkInfo, setBenchmarkInfo] = useState(null);

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

  const fetchCacheStats = async (showLoader = false) => {
    if (showLoader) setIsCacheLoading(true);
    setCacheError(null);
    try {
      const [statsRes, ringRes, distRes, batchRes, invalidationRes, metricsRes, latencyRes, benchmarkRes] = await Promise.all([
        axiosClient.get('/cache/stats'),
        axiosClient.get('/cache/ring'),
        axiosClient.get('/cache/distribution'),
        axiosClient.get('/batch/status').catch(() => ({ data: null })),
        axiosClient.get('/cache/invalidation/stats').catch(() => ({ data: null })),
        axiosClient.get('/metrics').catch(() => ({ data: null })),
        axiosClient.get('/metrics/latency').catch(() => ({ data: null })),
        axiosClient.get('/benchmark/report').catch(() => ({ data: null }))
      ]);
      setCacheInfo(statsRes.data);
      setRingInfo(ringRes.data);
      setDistInfo(distRes.data);
      if (batchRes && batchRes.data) {
        setBatchInfo(batchRes.data);
      }
      if (invalidationRes && invalidationRes.data) {
        setInvalidationInfo(invalidationRes.data);
      }
      if (metricsRes && metricsRes.data) {
        setMetricsInfo(metricsRes.data);
      }
      if (latencyRes && latencyRes.data) {
        setLatencyInfo(latencyRes.data);
      }
      if (benchmarkRes && benchmarkRes.data) {
        setBenchmarkInfo(benchmarkRes.data);
      }
    } catch (error) {
      setCacheError(error.message || 'Failed to fetch cache stats');
    } finally {
      if (showLoader) setIsCacheLoading(false);
    }
  };

  // Fetch stats on load
  useEffect(() => {
    fetchStats();
    fetchCacheStats(true);
  }, []);

  // Auto-refresh logic
  useEffect(() => {
    if (!autoRefresh) return;
    
    // Polling interval
    const interval = setInterval(() => {
      fetchCacheStats(false);
    }, 3000);
    
    return () => clearInterval(interval);
  }, [autoRefresh]);

  // Check if a node is offline based on distribution info
  const isNodeOffline = (nodeId) => {
    return distInfo && distInfo[nodeId] < 0;
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between selection:bg-indigo-500/30 relative overflow-x-hidden">
      <style>{`
        @keyframes pulseGlow {
          0%, 100% {
            transform: scale(1);
            opacity: 0.35;
          }
          50% {
            transform: scale(1.2);
            opacity: 0.7;
          }
        }
        @keyframes spinSlow {
          from {
            transform: rotate(0deg);
          }
          to {
            transform: rotate(360deg);
          }
        }
        .animate-spin-slow {
          animation: spinSlow 40s linear infinite;
          transform-origin: 100px 100px;
        }
      `}</style>

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
      </header>

      {/* Main Content */}
      <main className="flex-1 flex flex-col items-center justify-center px-6 py-16 z-10">
        <div className="w-full max-w-5xl text-center space-y-12">
          
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
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 text-xs font-semibold tracking-wide shadow-inner font-sans">
              Distributed Search Typeahead System
            </div>

            {/* Main Title */}
            <div className="space-y-4">
              <h1 className="text-5xl md:text-6xl font-extrabold tracking-tight bg-clip-text text-transparent bg-gradient-to-b from-white via-slate-100 to-slate-500 drop-shadow-sm font-sans">
                QueryFlow
              </h1>
              <p className="text-lg md:text-xl text-slate-400 max-w-xl mx-auto font-light leading-relaxed font-sans">
                High-performance query auto-suggestions backed by consistent hashing, caching, and batch write pipeline optimization.
              </p>
            </div>
          </div>

          {/* Search Box Component */}
          <div className="py-2 max-w-xl mx-auto w-full">
            <SearchBox trigger={searchTrigger} />
            <TrendingSearches onSelect={(q) => setSearchTrigger({ query: q, timestamp: Date.now() })} />
          </div>

          {/* System Control Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-4xl mx-auto w-full pt-6">
            
            {/* Health Card */}
            <div className="p-8 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl flex flex-col justify-between text-left hover:border-slate-700/60 transition-colors duration-300">
              <div className="space-y-2">
                <h3 className="text-lg font-bold text-slate-200 font-sans">System Health</h3>
                <p className="text-sm text-slate-400 font-light font-sans">Verify connection connectivity to QueryFlow's Java backend endpoints.</p>
              </div>

              <div className="space-y-4 mt-6">
                <button
                  id="check-health-btn"
                  onClick={checkHealth}
                  disabled={isHealthLoading}
                  className={`w-full py-3.5 px-6 rounded-xl font-medium tracking-wide transition-all duration-300 flex items-center justify-center gap-2 shadow-lg cursor-pointer font-sans ${
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
                  <div className="p-4 rounded-xl bg-emerald-500/5 border border-emerald-500/10 text-emerald-400 space-y-1.5 text-left text-sm transition-all duration-300 animate-fadeIn font-sans">
                    <div className="flex items-center gap-2 font-semibold">
                      <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping" />
                      <span>Backend Status: {healthInfo.status}</span>
                    </div>
                    <div className="text-xs text-emerald-500/60 font-mono">
                      Service Name: {healthInfo.service || 'QueryFlow'}
                    </div>
                  </div>
                )}

                {/* Error Info Display */}
                {healthError && (
                  <div className="p-4 rounded-xl bg-rose-500/5 border border-rose-500/10 text-rose-400 space-y-1 text-left text-sm transition-all duration-300 animate-fadeIn font-sans">
                    <div className="font-semibold flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-rose-500" />
                      <span>Connection Failed</span>
                    </div>
                    <p className="text-xs text-rose-500/60 leading-relaxed font-mono mt-1 break-all">
                      Error Details: {healthError}
                    </p>
                  </div>
                )}

                {/* Write Buffer Status */}
                {batchInfo && (
                  <div className="mt-4 p-4 rounded-2xl bg-slate-950/40 border border-slate-850/40 text-xs text-slate-400 space-y-2 animate-fadeIn">
                    <div className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">Write Buffer Status</div>
                    <div className="flex justify-between">
                      <span>Pending Events:</span>
                      <span className="font-mono text-indigo-400 font-semibold">{batchInfo.pendingEvents}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Buffered Entries:</span>
                      <span className="font-mono text-indigo-400 font-semibold">{batchInfo.pendingEntries}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Last Flush:</span>
                      <span className="font-mono text-slate-300 font-semibold">
                        {batchInfo.lastFlushTime ? new Date(batchInfo.lastFlushTime).toLocaleTimeString() : 'Never'}
                      </span>
                    </div>
                  </div>
                )}
                {/* Cache Invalidation Stats */}
                {invalidationInfo && (
                  <div className="mt-3 p-4 rounded-2xl bg-slate-950/40 border border-slate-850/40 text-xs text-slate-400 space-y-2 animate-fadeIn">
                    <div className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">Cache Invalidation</div>
                    <div className="flex justify-between">
                      <span>Invalidation Events:</span>
                      <span className="font-mono text-indigo-400 font-semibold">{invalidationInfo.invalidations}</span>
                    </div>
                    <div className="flex justify-between">
                      <span>Last Invalidation:</span>
                      <span className="font-mono text-slate-300 font-semibold">
                        {invalidationInfo.lastInvalidationTime ? new Date(invalidationInfo.lastInvalidationTime).toLocaleTimeString() : 'Never'}
                      </span>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Stats Card */}
            <div className="p-8 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl flex flex-col justify-between text-left hover:border-slate-700/60 transition-colors duration-300">
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <h3 className="text-lg font-bold text-slate-200 font-sans">Dataset Statistics</h3>
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
                <p className="text-sm text-slate-400 font-light font-sans">Total search queries parsed and loaded into the PostgreSQL storage engine.</p>
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
                  <div className="space-y-3 font-sans">
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
                  <div className="p-4 rounded-xl bg-rose-500/5 border border-rose-500/10 text-rose-400 text-left text-sm font-sans">
                    <div className="font-semibold text-xs uppercase tracking-wide">Failed to fetch stats</div>
                    <p className="text-xs text-rose-500/60 mt-1.5 leading-relaxed font-sans">{statsError}</p>
                  </div>
                ) : (
                  <div className="h-24 flex items-center justify-center text-slate-500 text-sm font-sans">
                    No statistics data available.
                  </div>
                )}
              </div>
            </div>

          </div>

          {/* Cache Ring & Metrics Dashboard Card */}
          <div className="max-w-5xl mx-auto w-full pt-4">
            <div className="p-8 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl hover:border-slate-700/60 transition-colors duration-300 text-left">
              
              {/* Header inside Dashboard Card */}
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 pb-6 border-b border-slate-900/80">
                <div className="space-y-1">
                  <h3 className="text-xl font-bold text-slate-200 flex items-center gap-2 font-sans">
                    <svg className="w-5 h-5 text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                    </svg>
                    Consistent Hashing & Cache Performance
                  </h3>
                  <p className="text-sm text-slate-400 font-light font-sans">Real-time Redis cache lookup performance statistics and consistent hashing ring key distribution.</p>
                </div>
                
                <div className="flex items-center gap-4 self-end sm:self-auto font-sans">
                  {/* Auto-Refresh Toggle */}
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-400">Auto-refresh</span>
                    <button
                      onClick={() => setAutoRefresh(!autoRefresh)}
                      className={`w-9 h-5 flex items-center rounded-full p-0.5 cursor-pointer transition-colors duration-300 ${
                        autoRefresh ? 'bg-indigo-600' : 'bg-slate-800 border border-slate-700'
                      }`}
                      aria-label="Toggle auto refresh"
                    >
                      <div
                        className={`bg-white w-4 h-4 rounded-full shadow-md transform transition-transform duration-300 ${
                          autoRefresh ? 'translate-x-4' : 'translate-x-0'
                        }`}
                      />
                    </button>
                  </div>

                  {/* Manual Refresh Button */}
                  <button 
                    onClick={() => fetchCacheStats(true)}
                    disabled={isCacheLoading}
                    className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800/60 border border-slate-800 hover:border-slate-700 transition-all cursor-pointer flex items-center gap-1.5 text-xs font-semibold"
                    title="Refresh cache stats"
                  >
                    <svg className={`h-4 w-4 ${isCacheLoading ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.21 7.89M9 11l3-3 3 3m-3-3v12" />
                    </svg>
                    Sync
                  </button>
                </div>
              </div>

              {/* Grid content */}
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 pt-6 items-center">
                
                {/* Left Column: Metrics & Distribution Bars (7 cols) */}
                <div className="lg:col-span-7 space-y-6">
                  
                  {/* Performance stats cards */}
                  <div className="grid grid-cols-3 gap-4 font-sans">
                    {/* Hit Rate */}
                    <div className="p-4 rounded-2xl bg-slate-950/50 border border-slate-850/60 shadow-inner flex flex-col justify-between">
                      <span className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">Hit Rate</span>
                      <span className="text-3xl font-black text-indigo-400 mt-1">
                        {cacheInfo ? `${cacheInfo.hitRate}%` : '—'}
                      </span>
                    </div>
                    {/* Hits */}
                    <div className="p-4 rounded-2xl bg-slate-950/50 border border-slate-850/60 shadow-inner flex flex-col justify-between">
                      <span className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">Total Hits</span>
                      <span className="text-2xl font-bold text-emerald-400 mt-1 font-mono">
                        {cacheInfo ? cacheInfo.hits : '—'}
                      </span>
                    </div>
                    {/* Misses */}
                    <div className="p-4 rounded-2xl bg-slate-950/50 border border-slate-850/60 shadow-inner flex flex-col justify-between">
                      <span className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">Total Misses</span>
                      <span className="text-2xl font-bold text-rose-400 mt-1 font-mono">
                        {cacheInfo ? cacheInfo.misses : '—'}
                      </span>
                    </div>
                  </div>

                  {/* Ring Details Badge */}
                  {ringInfo && (
                    <div className="p-4 rounded-2xl bg-slate-950/30 border border-slate-900/60 flex flex-col sm:flex-row gap-2 sm:gap-0 sm:items-center justify-between text-xs text-slate-400 font-sans">
                      <div className="flex items-center gap-2">
                        <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
                        <span>Consistent Hashing Ring: <strong className="text-slate-200">{ringInfo.totalNodes} Physical Nodes</strong></span>
                      </div>
                      <div>
                        <span>Replication Factor: <strong className="text-slate-200">{ringInfo.virtualNodes} Virtual Nodes</strong></span>
                      </div>
                    </div>
                  )}

                  {/* Distribution Progress Bars */}
                  <div className="space-y-4">
                    <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wider font-sans">Node Key Distribution</h4>
                    
                    {isCacheLoading && !distInfo ? (
                      <div className="h-32 flex items-center justify-center text-slate-500 text-sm font-sans">
                        <svg className="animate-spin h-5 w-5 text-indigo-400 mr-2" fill="none" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                        </svg>
                        Loading distribution metrics...
                      </div>
                    ) : distInfo ? (
                      <div className="space-y-3 font-sans">
                        {Object.entries(distInfo).map(([nodeId, count]) => {
                          const isOffline = count < 0;
                          
                          // Calculate percentage share
                          const activeEntries = Object.entries(distInfo).filter(([_, c]) => c >= 0);
                          const totalActiveKeys = activeEntries.reduce((sum, [_, c]) => sum + c, 0);
                          const percentage = !isOffline && totalActiveKeys > 0 ? (count / totalActiveKeys) * 100 : 0;
                          
                          // Define node theme color
                          let barColorClass = 'bg-gradient-to-r from-indigo-600 to-indigo-400 shadow-[0_0_8px_rgba(99,102,241,0.4)]';
                          let badgeColor = 'text-indigo-400 border-indigo-500/20 bg-indigo-500/10';
                          let nodeDotColor = 'bg-indigo-500';
                          
                          if (nodeId === 'redis-node-2') {
                            barColorClass = 'bg-gradient-to-r from-emerald-600 to-emerald-400 shadow-[0_0_8px_rgba(16,185,129,0.4)]';
                            badgeColor = 'text-emerald-400 border-emerald-500/20 bg-emerald-500/10';
                            nodeDotColor = 'bg-emerald-500';
                          } else if (nodeId === 'redis-node-3') {
                            barColorClass = 'bg-gradient-to-r from-cyan-600 to-cyan-400 shadow-[0_0_8px_rgba(6,182,212,0.4)]';
                            badgeColor = 'text-cyan-400 border-cyan-500/20 bg-cyan-500/10';
                            nodeDotColor = 'bg-cyan-500';
                          }

                          return (
                            <div 
                              key={nodeId} 
                              className={`p-4 rounded-2xl bg-slate-950/45 border transition-all duration-300 flex flex-col gap-2.5 ${
                                isOffline 
                                  ? 'border-rose-900/10 opacity-50 hover:opacity-70' 
                                  : 'border-slate-800/80 hover:border-slate-700/80'
                              }`}
                              onMouseEnter={() => setHoveredNodeId(nodeId)}
                              onMouseLeave={() => setHoveredNodeId(null)}
                            >
                              <div className="flex justify-between items-center text-xs">
                                <div className="flex items-center gap-2">
                                  <span className={`w-2 h-2 rounded-full ${isOffline ? 'bg-rose-500' : `${nodeDotColor} animate-pulse`}`} />
                                  <span className="font-semibold text-slate-200 font-mono tracking-tight">{nodeId}</span>
                                </div>
                                
                                <div className="flex items-center gap-2">
                                  {!isOffline && (
                                    <span className="text-[10px] text-slate-500 font-mono font-medium">
                                      {percentage.toFixed(0)}% share
                                    </span>
                                  )}
                                  <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded border ${
                                    isOffline 
                                      ? 'bg-rose-500/5 text-rose-400 border-rose-500/15' 
                                      : badgeColor
                                  }`}>
                                    {isOffline ? 'OFFLINE' : `${count} keys`}
                                  </span>
                                </div>
                              </div>

                              {/* Custom Progress Bar */}
                              <div className="w-full h-2 bg-slate-950 rounded-full border border-slate-900 overflow-hidden relative">
                                <div 
                                  className={`h-full rounded-full transition-all duration-500 ease-out ${
                                    isOffline ? 'w-0' : ''
                                  } ${barColorClass}`}
                                  style={{ width: isOffline ? '0%' : `${percentage}%` }}
                                />
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    ) : cacheError ? (
                      <div className="p-4 rounded-xl bg-rose-500/5 border border-rose-500/10 text-rose-400 text-left text-sm font-sans">
                        <div className="font-semibold text-xs uppercase tracking-wide">Failed to fetch stats</div>
                        <p className="text-xs text-rose-500/60 mt-1.5 leading-relaxed">{cacheError}</p>
                      </div>
                    ) : (
                      <div className="h-24 flex items-center justify-center text-slate-500 text-sm font-sans">
                        No cache statistics available.
                      </div>
                    )}
                  </div>
                </div>

                {/* Right Column: Consistent Hash Ring Diagram (5 cols) */}
                <div className="lg:col-span-5 flex flex-col items-center justify-center pt-6 lg:pt-0">
                  <div className="w-full max-w-[280px] aspect-square relative bg-slate-950/30 border border-slate-900 rounded-3xl p-6 flex items-center justify-center shadow-inner hover:border-slate-800 transition-colors duration-300">
                    
                    {/* SVG Consistent Hash Ring */}
                    <svg 
                      viewBox="0 0 200 200" 
                      className="w-full h-full drop-shadow-[0_0_15px_rgba(99,102,241,0.05)]"
                    >
                      {/* Base dashed ring */}
                      <circle 
                        cx="100" 
                        cy="100" 
                        r="70" 
                        fill="none" 
                        stroke="rgba(148, 163, 184, 0.12)" 
                        strokeWidth="1.5" 
                        strokeDasharray="4 4"
                        className="animate-spin-slow"
                      />

                      {/* Connection Lines from hovered Physical Node to its Virtual Nodes */}
                      {hoveredNodeId && (() => {
                        const pNode = physicalNodes.find(n => n.id === hoveredNodeId);
                        const isOffline = distInfo && distInfo[hoveredNodeId] < 0;
                        if (!pNode || isOffline) return null;

                        return virtualNodes
                          .filter(vNode => vNode.nodeId === hoveredNodeId)
                          .map(vNode => (
                            <line
                              key={`line-${vNode.id}`}
                              x1={pNode.x}
                              y1={pNode.y}
                              x2={vNode.x}
                              y2={vNode.y}
                              stroke={pNode.color}
                              strokeWidth="0.8"
                              strokeDasharray="2 3"
                              className="transition-all opacity-45 animate-pulse"
                            />
                          ));
                      })()}

                      {/* Virtual Nodes */}
                      {virtualNodes.map(vNode => {
                        const isParentOffline = distInfo && distInfo[vNode.nodeId] < 0;
                        const isParentHovered = hoveredNodeId === vNode.nodeId;
                        
                        // Adjust radius and opacity if hovered
                        const r = isParentHovered ? 3.5 : 1.8;
                        const opacity = isParentOffline ? 0.15 : (isParentHovered ? 1.0 : 0.45);
                        const fillColor = isParentOffline ? '#475569' : (isParentHovered ? vNode.hoverColor : vNode.color);

                        return (
                          <circle
                            key={`vnode-${vNode.id}`}
                            cx={vNode.x}
                            cy={vNode.y}
                            r={r}
                            fill={fillColor}
                            opacity={opacity}
                            className="transition-all duration-300"
                            style={{
                              filter: isParentHovered ? `drop-shadow(0 0 3px ${fillColor})` : 'none'
                            }}
                          />
                        );
                      })}

                      {/* Physical Nodes */}
                      {physicalNodes.map(node => {
                        const isOffline = distInfo && distInfo[node.id] < 0;
                        const isHovered = hoveredNodeId === node.id;
                        
                        const nodeColor = isOffline ? '#f43f5e' : node.color;
                        
                        return (
                          <g 
                            key={node.id}
                            className="cursor-pointer"
                            onMouseEnter={() => setHoveredNodeId(node.id)}
                            onMouseLeave={() => setHoveredNodeId(null)}
                          >
                            {/* Glowing Outer Pulse ring */}
                            <circle 
                              cx={node.x}
                              cy={node.y}
                              r={isHovered ? 13 : 10}
                              fill="none"
                              stroke={nodeColor}
                              strokeWidth="2.5"
                              opacity={isOffline ? 0.3 : 0.4}
                              style={{
                                animation: isOffline ? 'none' : 'pulseGlow 2.5s infinite ease-in-out',
                                transformOrigin: `${node.x}px ${node.y}px`
                              }}
                              className="transition-all duration-300"
                            />

                            {/* Inner Circle */}
                            <circle 
                              cx={node.x}
                              cy={node.y}
                              r={isHovered ? 7.5 : 5.5}
                              fill={nodeColor}
                              className="transition-all duration-300"
                              style={{
                                filter: `drop-shadow(0 0 6px ${nodeColor})`
                              }}
                            />
                          </g>
                        );
                      })}

                      {/* Center Info Text overlay */}
                      <g transform="translate(100, 100)">
                        {hoveredNodeId ? (() => {
                          const isOffline = distInfo && distInfo[hoveredNodeId] < 0;
                          const nodeLabel = hoveredNodeId === 'redis-node-1' ? 'Node 1' : (hoveredNodeId === 'redis-node-2' ? 'Node 2' : 'Node 3');
                          return (
                            <>
                              <text textAnchor="middle" y="-12" className="text-[10px] font-bold fill-slate-400 uppercase tracking-widest font-mono">
                                {nodeLabel}
                              </text>
                              <text textAnchor="middle" y="5" className={`text-[12px] font-black uppercase tracking-wide ${isOffline ? 'fill-rose-500' : 'fill-emerald-400'}`}>
                                {isOffline ? 'Offline' : 'Online'}
                              </text>
                              <text textAnchor="middle" y="21" className="text-[9px] font-medium fill-slate-500 font-mono">
                                {isOffline ? '—' : `${distInfo[hoveredNodeId]} keys`}
                              </text>
                            </>
                          );
                        })() : (
                          <>
                            <text textAnchor="middle" y="-5" className="text-[10px] font-bold fill-indigo-400/90 uppercase tracking-wider font-mono">
                              Hash Ring
                            </text>
                            <text textAnchor="middle" y="10" className="text-[8px] font-semibold fill-slate-500 uppercase tracking-widest font-mono">
                              3 Nodes
                            </text>
                          </>
                        )}
                      </g>
                    </svg>

                    {/* Outer instructions label */}
                    <div className="absolute bottom-2 text-[9px] text-slate-500 font-semibold tracking-wider uppercase font-sans">
                      Hover nodes to see topology
                    </div>
                  </div>
                </div>

              </div>

            </div>
          </div>

          {/* Developer Metrics & Diagnostics Dashboard */}
          <div className="max-w-5xl mx-auto w-full pt-4">
            <DeveloperDashboard 
              metrics={metricsInfo} 
              latency={latencyInfo} 
              benchmark={benchmarkInfo} 
            />
          </div>

        </div>
      </main>

      {/* Footer */}
      <footer className="w-full max-w-7xl mx-auto px-6 py-8 border-t border-slate-900/60 flex flex-col md:flex-row justify-between items-center gap-4 text-sm text-slate-500 z-10 font-sans">
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
