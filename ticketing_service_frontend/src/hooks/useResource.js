import { useCallback, useEffect, useState } from 'react'
import { api } from '../lib/api'

export default function useResource(path) {
  const [state, setState] = useState({ path: null, data: null, error: null, loading: true })
  const [revision, setRevision] = useState(0)
  const reload = useCallback(() => setRevision(value => value + 1), [])
  useEffect(() => {
    const controller = new AbortController()
    api(path, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setState({ path, data, error: null, loading: false })
    }).catch(error => {
      if (!controller.signal.aborted) setState({ path, data: null, error, loading: false })
    })
    return () => controller.abort()
  }, [path, revision])
  return { ...(state.path === path ? state : { data: null, error: null, loading: true }), reload }
}
