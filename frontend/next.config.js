/** @type {import('next').NextConfig} */
const backendApiBaseUrl = (
  process.env.BACKEND_API_BASE_URL?.trim() || 'http://localhost:8080'
).replace(/\/$/, '');

const nextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${backendApiBaseUrl}/api/:path*`
      }
    ];
  }
};

module.exports = nextConfig;
