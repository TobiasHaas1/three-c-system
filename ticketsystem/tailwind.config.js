/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        'repo': '#a4c639',       // Your Main Green
        'repo-dark': '#8fb536',  // Hover Green
      }
    },
  },
  plugins: [],
}
