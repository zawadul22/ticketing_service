import { useEffect, useState } from 'react'

const KEY = 'gather.theme'

function preferredTheme() {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch { /* Follow the system when storage is unavailable. */ }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export default function useTheme() {
  const [theme, setTheme] = useState(preferredTheme)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#171c18' : '#fbfaf7')
  }, [theme])

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const systemChanged = () => setTheme(preferredTheme())
    const storageChanged = event => { if (event.key === KEY || event.key === null) systemChanged() }
    media.addEventListener('change', systemChanged)
    window.addEventListener('storage', storageChanged)
    return () => {
      media.removeEventListener('change', systemChanged)
      window.removeEventListener('storage', storageChanged)
    }
  }, [])

  function toggleTheme() {
    const next = theme === 'dark' ? 'light' : 'dark'
    try { localStorage.setItem(KEY, next) } catch { /* The toggle still works for this visit. */ }
    setTheme(next)
  }

  return { theme, toggleTheme }
}
