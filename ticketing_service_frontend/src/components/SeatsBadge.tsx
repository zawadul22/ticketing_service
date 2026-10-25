export function SeatsBadge({ available }: { available: number }) {
  if (available === 0) return <span className="badge badge-danger">Sold out</span>
  if (available <= 10) return <span className="badge badge-warn">{available} left</span>
  return <span className="badge">{available} seats left</span>
}
