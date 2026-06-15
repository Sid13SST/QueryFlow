import { useState, useEffect, useRef } from 'react';
import axiosClient from '../api/axiosClient';

export default function SearchBox({ trigger }) {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  
  const [isSearching, setIsSearching] = useState(false);
  const [searchFeedback, setSearchFeedback] = useState('');
  
  const containerRef = useRef(null);

  // Close dropdown on click outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setShowDropdown(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Debouncing effect
  useEffect(() => {
    const trimmedQuery = query.trim();
    if (trimmedQuery === '') {
      setSuggestions([]);
      setErrorMsg('');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setErrorMsg('');

    const delayDebounceFn = setTimeout(async () => {
      try {
        const response = await axiosClient.get(`/suggest?q=${encodeURIComponent(trimmedQuery)}`);
        setSuggestions(response.data);
        setShowDropdown(true);
      } catch (err) {
        console.error('Error fetching suggestions:', err);
        setErrorMsg(err.message || 'Error loading suggestions');
        setSuggestions([]);
      } finally {
        setIsLoading(false);
      }
    }, 300); // 300ms debounce delay

    return () => clearTimeout(delayDebounceFn);
  }, [query]);

  // Submit search request
  const submitSearch = async (value) => {
    const trimmedVal = value.trim();
    if (trimmedVal === '') return;

    setIsSearching(true);
    setSearchFeedback('Searching...');
    
    try {
      await axiosClient.post('/search', { query: trimmedVal });
      setSearchFeedback('Search recorded');
      setTimeout(() => setSearchFeedback(''), 2500); // Clear feedback after 2.5s
    } catch (err) {
      console.error('Error submitting search:', err);
      setSearchFeedback('Failed to record search');
      setTimeout(() => setSearchFeedback(''), 3000);
    } finally {
      setIsSearching(false);
    }
  };

  useEffect(() => {
    if (trigger && trigger.query) {
      setQuery(trigger.query);
      setShowDropdown(false);
      submitSearch(trigger.query);
    }
  }, [trigger]);

  const handleSelectSuggestion = (value) => {
    setQuery(value);
    setShowDropdown(false);
    submitSearch(value);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      setShowDropdown(false);
      submitSearch(query);
    }
  };

  return (
    <div ref={containerRef} className="relative w-full max-w-lg mx-auto z-25 text-left">
      <div className="relative">
        <input
          type="text"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setShowDropdown(true);
          }}
          onKeyDown={handleKeyDown}
          placeholder="Start typing to search (e.g. iphone, java)..."
          className="w-full py-4.5 pl-12 pr-12 rounded-2xl bg-slate-900/80 border border-slate-800 focus:border-indigo-500/80 focus:ring-2 focus:ring-indigo-500/20 text-slate-100 placeholder-slate-500 outline-none transition-all duration-300 shadow-xl backdrop-blur-md text-base"
          onFocus={() => setShowDropdown(true)}
          disabled={isSearching}
        />
        
        {/* Search Icon */}
        <div className="absolute left-4.5 top-1/2 -translate-y-1/2 text-slate-500">
          <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
        </div>

        {/* Loading Spinner / Clear Button */}
        <div className="absolute right-4.5 top-1/2 -translate-y-1/2 flex items-center gap-2">
          {isLoading && (
            <svg className="animate-spin h-5 w-5 text-indigo-500" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
            </svg>
          )}
          {!isLoading && query && (
            <button
              onClick={() => {
                setQuery('');
                setSuggestions([]);
              }}
              className="text-slate-500 hover:text-slate-300 p-0.5 rounded-lg transition-colors cursor-pointer"
            >
              <svg className="h-4.5 w-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* Search Feedback Indicator */}
      {searchFeedback && (
        <div className="absolute right-2.5 -bottom-7 flex items-center gap-1.5 text-[10px] font-semibold tracking-wide uppercase px-2.5 py-0.5 rounded-full border border-slate-800/80 bg-slate-900/90 shadow-lg animate-fadeIn z-20">
          <span className={`w-1.5 h-1.5 rounded-full ${
            searchFeedback === 'Searching...' 
              ? 'bg-amber-500 animate-pulse' 
              : searchFeedback === 'Search recorded' 
                ? 'bg-emerald-500' 
                : 'bg-rose-500'
          }`} />
          <span className={
            searchFeedback === 'Searching...' 
              ? 'text-amber-400' 
              : searchFeedback === 'Search recorded' 
                ? 'text-emerald-400' 
                : 'text-rose-400'
          }>
            {searchFeedback}
          </span>
        </div>
      )}

      {/* Suggestions Dropdown */}
      {showDropdown && query.trim() !== '' && !isSearching && (
        <div className="absolute left-0 right-0 mt-2 rounded-2xl bg-slate-900 border border-slate-800 shadow-2xl overflow-hidden backdrop-blur-xl animate-fadeIn z-30">
          
          {/* Suggestions List */}
          {suggestions.length > 0 && (
            <ul className="py-2.5 max-h-80 overflow-y-auto">
              {suggestions.map((item, index) => (
                <li
                  key={index}
                  onClick={() => handleSelectSuggestion(item.query)}
                  className="px-5 py-3 hover:bg-indigo-600/15 cursor-pointer flex items-center justify-between transition-colors group"
                >
                  <div className="flex items-center gap-3">
                    <svg className="h-4 w-4 text-slate-500 group-hover:text-indigo-400 transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                    <span className="text-slate-200 group-hover:text-white transition-colors text-sm md:text-base font-light">
                      {item.query}
                    </span>
                  </div>
                  <span className="text-xs font-mono font-medium px-2 py-0.5 rounded bg-slate-950 text-slate-400 group-hover:text-indigo-300 group-hover:bg-indigo-500/10 border border-slate-800 group-hover:border-indigo-500/20 transition-all">
                    {item.count.toLocaleString()}
                  </span>
                </li>
              ))}
            </ul>
          )}

          {/* Empty State */}
          {!isLoading && suggestions.length === 0 && !errorMsg && (
            <div className="px-5 py-6 text-center text-slate-500 text-sm">
              No matching suggestions found. Press Enter to submit custom search.
            </div>
          )}

          {/* Error State */}
          {errorMsg && (
            <div className="px-5 py-4 text-sm text-rose-500 bg-rose-500/5 border-t border-slate-800 flex items-center gap-2">
              <svg className="h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
              <span className="truncate">{errorMsg}</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
