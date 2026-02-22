import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#121619',
        slate: '#2E3A44',
        mist: '#E9EEF2',
        mint: '#57C785',
        ember: '#F45D48'
      }
    }
  },
  plugins: []
};

export default config;
