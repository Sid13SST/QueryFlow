import { useCallback, useEffect, useRef, useState } from 'react';
import axiosClient from '../api/axiosClient';

// A free Render instance sleeps after ~15 min without traffic. The first request wakes it
// and is held open until the app is up (typically 30-60 s), so the probe uses a long timeout
// and keeps retrying for a while before asking the visitor to retry by hand.
const PROBE_TIMEOUT_MS = 70000;
const RETRY_DELAY_MS = 3000;
const GIVE_UP_AFTER_MS = 150000;
// Only show the wake-up notice if the first answer takes longer than this.
const WAKING_NOTICE_DELAY_MS = 2500;

/**
 * Tracks whether the backend is reachable.
 * status: 'connecting' (first probe, quiet) | 'waking' | 'ready' | 'asleep' (gave up)
 */
export default function useBackendStatus() {
  const [status, setStatus] = useState('connecting');
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const runRef = useRef(0);
  const statusRef = useRef(status);
  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  const probe = useCallback(async ({ reset = true } = {}) => {
    const run = ++runRef.current;
    const startedAt = Date.now();
    const isCurrent = () => runRef.current === run;

    // The first probe starts from the initial state; later ones (retry, back to sleep) reset it.
    if (reset) {
      setStatus('connecting');
      setElapsedSeconds(0);
    }
    const noticeTimer = setTimeout(() => {
      if (isCurrent()) setStatus((s) => (s === 'connecting' ? 'waking' : s));
    }, WAKING_NOTICE_DELAY_MS);
    const ticker = setInterval(() => {
      if (isCurrent()) setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);

    try {
      while (isCurrent()) {
        try {
          await axiosClient.get('/health', { timeout: PROBE_TIMEOUT_MS });
          if (isCurrent()) setStatus('ready');
          return;
        } catch (error) {
          if (!isCurrent()) return;
          // The API answered with a real (non-gateway) error: it is awake.
          if (!error.unreachable) {
            setStatus('ready');
            return;
          }
          setStatus('waking');
          if (Date.now() - startedAt + RETRY_DELAY_MS >= GIVE_UP_AFTER_MS) {
            setStatus('asleep');
            return;
          }
          await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
        }
      }
    } finally {
      clearTimeout(noticeTimer);
      clearInterval(ticker);
    }
  }, []);

  useEffect(() => {
    // Started from a timer callback so the effect itself never sets state synchronously.
    const start = setTimeout(() => probe({ reset: false }), 0);
    return () => {
      clearTimeout(start);
      runRef.current += 1; // cancel the running probe on unmount
    };
  }, [probe]);

  /** Called when a later request finds the API unreachable (e.g. it went back to sleep). */
  const reportUnreachable = useCallback(() => {
    if (statusRef.current === 'ready') probe();
  }, [probe]);

  const retry = useCallback(() => probe(), [probe]);

  return { status, ready: status === 'ready', elapsedSeconds, retry, reportUnreachable };
}
