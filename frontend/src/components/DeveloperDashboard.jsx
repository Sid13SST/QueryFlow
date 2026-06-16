import React from 'react';

function DeveloperDashboard({ metrics, latency, benchmark }) {
  if (!metrics || !latency || !benchmark) {
    return (
      <div className="p-8 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl text-center text-slate-500 font-sans">
        <svg className="animate-spin h-6 w-6 text-indigo-400 mx-auto mb-3" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
        Initializing Developer Diagnostics...
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-fadeIn font-sans text-left">
      {/* Title */}
      <div className="flex items-center gap-2 pb-2 border-b border-slate-900/80">
        <svg className="w-5 h-5 text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
        <h3 className="text-xl font-bold text-slate-200">Developer Metrics & Observability</h3>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        
        {/* Card 1: Cache Efficiency */}
        <div className="p-6 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl flex flex-col justify-between hover:border-slate-700/60 transition-colors duration-300">
          <div className="space-y-1">
            <span className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">Cache Performance</span>
            <h4 className="text-sm font-bold text-slate-300">Hit & Miss Ratio</h4>
          </div>
          
          <div className="my-4 space-y-3">
            <div className="flex justify-between items-end">
              <span className="text-3xl font-black text-indigo-400">{metrics.cacheHitRate}%</span>
              <span className="text-[10px] text-slate-500 font-mono">Hit Rate</span>
            </div>
            
            {/* Custom progress bar */}
            <div className="w-full h-1.5 bg-slate-950 rounded-full overflow-hidden">
              <div 
                className="h-full bg-gradient-to-r from-indigo-500 to-indigo-400"
                style={{ width: `${metrics.cacheHitRate}%` }}
              />
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs font-mono pt-1 text-slate-400">
              <div className="flex justify-between border-r border-slate-800/60 pr-2">
                <span>Hits:</span>
                <span className="text-emerald-400 font-semibold">{metrics.cacheHits}</span>
              </div>
              <div className="flex justify-between pl-1">
                <span>Misses:</span>
                <span className="text-rose-400 font-semibold">{metrics.cacheMisses}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Card 2: Database Operations */}
        <div className="p-6 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl flex flex-col justify-between hover:border-slate-700/60 transition-colors duration-300">
          <div className="space-y-1">
            <span className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">Database Savings</span>
            <h4 className="text-sm font-bold text-slate-300">Reads vs. Writes</h4>
          </div>
          
          <div className="my-4 space-y-2">
            <div className="flex justify-between items-center text-xs">
              <span className="text-slate-400">Database Reads:</span>
              <span className="font-mono text-indigo-400 font-bold">{metrics.databaseReads}</span>
            </div>
            <div className="flex justify-between items-center text-xs">
              <span className="text-slate-400">Database Writes:</span>
              <span className="font-mono text-indigo-400 font-bold">{metrics.databaseWrites}</span>
            </div>
            
            <div className="border-t border-slate-900/60 my-2 pt-2 flex justify-between items-center text-xs font-semibold">
              <span className="text-emerald-400">Reads Saved (Hits):</span>
              <span className="font-mono text-emerald-400">+{benchmark.estimatedDbReadsSaved}</span>
            </div>
          </div>
        </div>

        {/* Card 3: Write Aggregation */}
        <div className="p-6 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl flex flex-col justify-between hover:border-slate-700/60 transition-colors duration-300">
          <div className="space-y-1">
            <span className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">Write Aggregator</span>
            <h4 className="text-sm font-bold text-slate-300">Batch Buffer Flushes</h4>
          </div>
          
          <div className="my-4 space-y-3">
            <div className="flex justify-between items-end">
              <span className="text-3xl font-black text-emerald-400">{benchmark.estimatedWriteReduction}%</span>
              <span className="text-[10px] text-slate-500 font-mono">Reduction</span>
            </div>
            
            <div className="w-full h-1.5 bg-slate-950 rounded-full overflow-hidden">
              <div 
                className="h-full bg-gradient-to-r from-emerald-500 to-emerald-400"
                style={{ width: `${benchmark.estimatedWriteReduction}%` }}
              />
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs font-mono pt-1 text-slate-400">
              <div className="flex justify-between border-r border-slate-800/60 pr-2">
                <span>Flushes:</span>
                <span className="text-slate-200 font-semibold">{metrics.batchFlushes}</span>
              </div>
              <div className="flex justify-between pl-1">
                <span>Avg Duration:</span>
                <span className="text-indigo-400 font-semibold">{metrics.averageFlushDurationMs}ms</span>
              </div>
            </div>
          </div>
        </div>

        {/* Card 4: Cache Invalidations */}
        <div className="p-6 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl flex flex-col justify-between hover:border-slate-700/60 transition-colors duration-300">
          <div className="space-y-1">
            <span className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold">Cache Coherency</span>
            <h4 className="text-sm font-bold text-slate-300">Active Invalidations</h4>
          </div>
          
          <div className="my-4 space-y-4">
            <div className="p-3 rounded-xl bg-slate-950/80 border border-slate-900 flex items-center justify-between shadow-inner">
              <div>
                <span className="text-[9px] text-slate-500 uppercase font-semibold">Keys Evicted</span>
                <span className="text-2xl font-black text-indigo-400 block mt-0.5">{metrics.cacheInvalidations}</span>
              </div>
              <div className="w-9 h-9 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </div>
            </div>
            <div className="text-[10px] text-slate-500 text-left font-medium leading-tight">
              Targeted deletions triggered per-node immediately after batch updates to PostgreSQL.
            </div>
          </div>
        </div>

      </div>

      {/* Latency Section */}
      <div className="p-6 rounded-3xl bg-slate-900/20 border border-slate-800/60 backdrop-blur-xl shadow-2xl hover:border-slate-700/60 transition-colors duration-300">
        <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">Request Throughput Latencies</h4>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          
          {/* Suggestion API Latency */}
          <div className="p-4 rounded-2xl bg-slate-950/45 border border-slate-800/80 flex items-center justify-between">
            <div className="space-y-1">
              <span className="text-[10px] text-slate-500 font-semibold uppercase">Suggestion API</span>
              <div className="flex items-baseline gap-1.5">
                <span className="text-xl font-bold text-slate-200">{latency.suggestionAvgMs}ms</span>
                <span className="text-[10px] text-slate-500">average</span>
              </div>
            </div>
            <div className="text-right">
              <span className="text-[9px] text-rose-500 uppercase font-semibold">Max Latency</span>
              <span className="block text-sm font-semibold text-rose-400 font-mono mt-0.5">{latency.suggestionMaxMs}ms</span>
            </div>
          </div>

          {/* Trending API Latency */}
          <div className="p-4 rounded-2xl bg-slate-950/45 border border-slate-800/80 flex items-center justify-between">
            <div className="space-y-1">
              <span className="text-[10px] text-slate-500 font-semibold uppercase">Trending API</span>
              <div className="flex items-baseline gap-1.5">
                <span className="text-xl font-bold text-slate-200">{latency.trendingAvgMs}ms</span>
                <span className="text-[10px] text-slate-500">average</span>
              </div>
            </div>
            <div className="text-right">
              <span className="text-[9px] text-rose-500 uppercase font-semibold">Max Latency</span>
              <span className="block text-sm font-semibold text-rose-400 font-mono mt-0.5">{latency.trendingMaxMs}ms</span>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}

export default DeveloperDashboard;
