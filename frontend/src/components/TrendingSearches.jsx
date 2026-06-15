import { useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';

export default function TrendingSearches({ onSelect }) {
  const [trending, setTrending] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const fetchTrending = async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await axiosClient.get('/trending');
      setTrending(response.data);
    } catch (err) {
      console.error('Error fetching trending searches:', err);
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTrending();
    
    // Refresh trending list when page gets focused or periodically
    const interval = setInterval(fetchTrending, 15000); // 15 seconds poll for trending updates
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="w-full max-w-xl mx-auto mt-6 text-center animate-fadeIn font-sans">
      <div className="flex items-center justify-center gap-2 mb-2.5">
        <span className="text-sm font-bold tracking-wider text-slate-400 uppercase flex items-center gap-1.5">
          <span className="text-amber-500 animate-pulse">🔥</span> Trending Searches
        </span>
      </div>

      {loading && trending.length === 0 ? (
        <div className="flex justify-center items-center gap-2 py-2 text-xs text-slate-500 font-light">
          <svg className="animate-spin h-3.5 w-3.5 text-indigo-400" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
          Loading popular searches...
        </div>
      ) : error ? (
        <div className="py-2 text-xs text-rose-400 font-medium bg-rose-500/5 border border-rose-500/10 rounded-xl inline-block px-4">
          Unable to load trending searches
        </div>
      ) : trending.length === 0 ? (
        <div className="py-2 text-xs text-slate-500 font-light bg-slate-900/40 border border-slate-800/40 rounded-xl inline-block px-4">
          No trending searches available
        </div>
      ) : (
        <div className="flex flex-wrap gap-2.5 justify-center items-center">
          {trending.map((item, idx) => (
            <button
              key={idx}
              onClick={() => onSelect(item.query)}
              className="group px-3 py-1.5 rounded-full bg-slate-900/60 border border-slate-800/60 hover:border-indigo-500/30 hover:bg-indigo-950/10 active:scale-95 transition-all duration-200 cursor-pointer flex items-center gap-2 shadow-sm text-left"
            >
              <span className="text-[10px] font-mono font-bold w-4 h-4 flex items-center justify-center rounded bg-slate-950 text-slate-500 group-hover:text-indigo-400 group-hover:bg-indigo-500/10 transition-colors border border-slate-850">
                {idx + 1}
              </span>
              <span className="text-xs text-slate-300 group-hover:text-white font-light transition-colors">
                {item.query}
              </span>
              {!loading && (
                <span className="text-[9px] font-mono text-slate-600 group-hover:text-indigo-500/70 transition-colors">
                  ({item.count.toLocaleString()})
                </span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
