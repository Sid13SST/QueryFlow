import axios from 'axios';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 5000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
});

/**
 * True when the backend could not be reached at all: no HTTP response (network error,
 * timeout, CORS failure while the host is starting) or a gateway error from the hosting
 * proxy (502/503/504), which is what a sleeping free-tier instance returns while it boots.
 */
export const isUnreachableError = (error) =>
  !error?.response || [502, 503, 504].includes(error.response.status);

// Interceptor for clean error handling
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Standardize error formats for easier rendering
    const customError = {
      message: error.response?.data?.message || error.message || 'An error occurred',
      status: error.response?.status,
      timestamp: error.response?.data?.timestamp || new Date().toISOString(),
      path: error.response?.data?.path || '',
      unreachable: isUnreachableError(error),
    };
    return Promise.reject(customError);
  }
);

export default axiosClient;
