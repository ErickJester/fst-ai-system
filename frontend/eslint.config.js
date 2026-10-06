// Revisión automática del código: npm run lint
import js from '@eslint/js'
import globals from 'globals'
import react from 'eslint-plugin-react'
import reactHooks from 'eslint-plugin-react-hooks'

export default [
  { ignores: ['dist/'] },
  js.configs.recommended,
  {
    files: ['src/**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    plugins: { react, 'react-hooks': reactHooks },
    rules: {
      ...react.configs.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      'react/prop-types': 'off', // sin PropTypes en este proyecto
      'react/no-unescaped-entities': 'off', // los textos en español llevan comillas y apóstrofos
      'no-unused-vars': ['error', { varsIgnorePattern: '^React$', ignoreRestSiblings: true }],
    },
  },
  { files: ['src/**/*.test.js'], languageOptions: { globals: globals.node } },
]
