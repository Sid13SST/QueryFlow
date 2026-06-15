import axios from 'axios';

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 5000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
});

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
    };
    return Promise.reject(customError);
  }
);

export default axiosClient;
