/**
 * UserAvatar.test.tsx — shared avatar identity contract (AVATAR-01…06, 12-UI-SPEC)
 *
 * Tests:
 * 1. dicebearUrl builds the pinned 10.x thumbs URL byte-exact (AVATAR-01..06 URL contract)
 * 2. Seeds containing reserved characters are percent-encoded via encodeURIComponent
 * 3. UserAvatar renders the initials fallback in jsdom (radix AvatarImage never
 *    fires load in jsdom, so the fallback is what renders — assert on it)
 * 4. getInitials edge cases: double spaces filtered, single word, max 2 initials
 * 5. className passthrough lands on the rendered root element (64px profile case)
 */
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { UserAvatar, dicebearUrl } from '../UserAvatar';
import { getInitials } from '../../../lib/utils';

describe('dicebearUrl', () => {
  it('Test 1: builds the pinned 10.x thumbs URL byte-exact (AVATAR-01..06)', () => {
    expect(dicebearUrl('abc')).toBe(
      'https://api.dicebear.com/10.x/thumbs/svg?seed=abc',
    );
  });

  it('Test 2: percent-encodes reserved characters in the seed', () => {
    expect(dicebearUrl('a&b?c=d')).toBe(
      `https://api.dicebear.com/10.x/thumbs/svg?seed=${encodeURIComponent('a&b?c=d')}`,
    );
    // sanity: the raw reserved characters must not survive in the query value
    expect(dicebearUrl('a&b?c=d')).not.toContain('a&b');
  });
});

describe('UserAvatar', () => {
  it('Test 3: renders initials fallback in jsdom (AVATAR fallback contract)', () => {
    render(<UserAvatar userId="u1" displayName="Jane Doe" />);
    expect(screen.getByText('JD')).toBeInTheDocument();
  });

  it('Test 5: className passthrough appears on the rendered root element', () => {
    const { container } = render(
      <UserAvatar userId="u1" displayName="Jane Doe" className="h-16 w-16" />,
    );
    const root = container.querySelector('[data-slot="avatar"]');
    expect(root).not.toBeNull();
    expect(root!.className).toContain('h-16');
    expect(root!.className).toContain('w-16');
  });
});

describe('getInitials', () => {
  it('Test 4: handles double spaces, single word, and caps at 2 initials', () => {
    expect(getInitials('Jane  Doe')).toBe('JD');
    expect(getInitials('Cher')).toBe('C');
    expect(getInitials('Ada Lovelace King')).toBe('AL');
  });
});
