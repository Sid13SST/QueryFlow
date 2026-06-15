/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#f5f7ff',
          100: '#ebf0ff',
          200: '#dbe2ff',
          300: '#bfcbff',
          400: '#99a8ff',
          500: '#6d7aff',
          600: '#545be6',
          700: '#4247cc',
          800: '#3639a6',
          900: '#303285',
          950: '#1d1e4e',
        }
      }
    },
  },
  plugins: [],
}
