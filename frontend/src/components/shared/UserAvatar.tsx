/**
 * UserAvatar.tsx — shared Dicebear avatar with initials fallback (AVATAR-01…06)
 *
 * Single source of truth for user avatars. Never construct Dicebear URLs inline
 * in pages — always render this component (or call the exported dicebearUrl).
 *
 * Seed is ALWAYS the user UUID, never displayName (display names collide
 * between users and are mutable; UUIDs are unique, stable, and already public
 * in SPA routes like /users/:id).
 *
 * Security (T-12-09): the SVG is referenced exclusively via <img src> — scripts
 * are inert in image context. Never fetch-and-inline Dicebear SVG markup.
 */
import { Avatar, AvatarImage, AvatarFallback } from '../ui/avatar';
import { getInitials } from '../../lib/utils';

/** Dicebear style — thumbs (CC0 1.0). */
export const DICEBEAR_STYLE = 'thumbs';

/**
 * Dicebear API version — pinned FOREVER. The same seed renders differently
 * across Dicebear major versions (verified live 2026-07-08), so bumping this
 * silently changes every user's avatar. Never change it.
 */
export const DICEBEAR_VERSION = '10.x';

/** Build the deterministic Dicebear SVG URL for a seed (user UUID). */
export function dicebearUrl(seed: string): string {
  return `https://api.dicebear.com/${DICEBEAR_VERSION}/${DICEBEAR_STYLE}/svg?seed=${encodeURIComponent(seed)}`;
}

interface Props {
  /** User UUID — the avatar seed. Never pass a display name here. */
  userId: string;
  /** Display name — used only for the initials fallback. */
  displayName: string;
  size?: 'sm' | 'default' | 'lg';
  className?: string;
  fallbackClassName?: string;
}

export function UserAvatar({
  userId,
  displayName,
  size = 'default',
  className,
  fallbackClassName,
}: Props) {
  return (
    <Avatar size={size} className={className}>
      {/* anonymous crossOrigin → real CORS 200s, NOT opaque responses —
          avoids Chrome opaque-response quota padding in the SW cache.
          Dicebear sends access-control-allow-origin: * (verified live).
          alt="" — decorative; the display name is always adjacent text. */}
      <AvatarImage src={dicebearUrl(userId)} alt="" crossOrigin="anonymous" />
      <AvatarFallback className={fallbackClassName}>
        {getInitials(displayName)}
      </AvatarFallback>
    </Avatar>
  );
}
