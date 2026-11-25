import { API_BASE_URL } from '../api/axiosClient';

const Spinner = () => (
  <svg className="animate-spin h-5 w-5 text-indigo-400 shrink-0" fill="none" viewBox="0 0 24 24" aria-hidden="true">
    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
  </svg>
);

/** Friendly notice while the free-tier backend cold-starts, with a manual retry if it takes too long. */
export default function BackendStatusBanner({ status, elapsedSeconds, onRetry }) {
  if (status === 'ready' || status === 'connecting') return null;

  if (status === 'asleep') {
    return (
      <div role="alert" className="w-full max-w-xl mx-auto p-5 rounded-2xl bg-amber-500/5 border border-amber-500/20 text-left font-sans animate-fadeIn">
        <div className="flex items-start gap-3">
          <span className="mt-1.5 w-2 h-2 rounded-full bg-amber-500 shrink-0" />
          <div className="min-w-0 space-y-2">
            <p className="text-sm font-semibold text-amber-300">The backend has not answered yet</p>
            <p className="text-sm text-slate-400 font-light leading-relaxed">
              It runs on a free instance that sleeps when nobody is using it, and waking it occasionally takes longer
              than usual. Give it another try; nothing is lost.
            </p>
            <p className="text-[11px] text-slate-500 font-mono break-all">API: {API_BASE_URL}</p>
            <button
              type="button"
              onClick={onRetry}
              className="mt-1 py-2 px-4 rounded-xl text-sm font-medium bg-gradient-to-r from-indigo-600 to-blue-500 hover:from-indigo-500 hover:to-blue-400 text-white shadow-lg shadow-indigo-500/15 active:scale-[0.98] transition-all cursor-pointer"
            >
              Retry now
            </button>
          </div>
        </div>
      </div>
    );
  }

  // waking
  const progress = Math.min(95, Math.round((elapsedSeconds / 60) * 100));
  return (
    <div role="status" aria-live="polite" className="w-full max-w-xl mx-auto p-5 rounded-2xl bg-indigo-500/5 border border-indigo-500/20 text-left font-sans animate-fadeIn">
      <div className="flex items-start gap-3">
        <Spinner />
        <div className="min-w-0 flex-1 space-y-2">
          <p className="text-sm font-semibold text-indigo-300">
            Waking up the backend<span className="text-slate-500 font-normal"> · {elapsedSeconds}s</span>
          </p>
          <p className="text-sm text-slate-400 font-light leading-relaxed">
            The API runs on a free-tier instance that sleeps after 15 minutes of inactivity. The first visit starts it
            again, which can take up to about a minute. Search, trending and the live dashboard load as soon as it is up.
          </p>
          <div className="h-1.5 w-full rounded-full bg-slate-900 border border-slate-800 overflow-hidden">
            <div
              className="h-full rounded-full bg-gradient-to-r from-indigo-600 to-indigo-400 transition-all duration-1000 ease-out"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
