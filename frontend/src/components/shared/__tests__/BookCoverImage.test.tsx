/**
 * BookCoverImage.test.tsx — gradient fallback contract (UI-07, D-08)
 *
 * Tests:
 * 1. When no cover source is available and the parent passes an aspect-[2/3]
 *    className, the rendered fallback element carries aspect-[2/3] (UI-07 —
 *    the 2:3 cover box must hold even for the gradient fallback)
 * 2. The fallback initial uses the design-system Heading weight/size
 *    font-semibold text-xl — font-bold is banned in application code (D-08)
 */
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { BookCoverImage } from '../BookCoverImage';

describe('BookCoverImage fallback', () => {
  it('Test 1: fallback element carries the parent-passed aspect-[2/3] className (UI-07)', () => {
    render(
      <BookCoverImage
        coverId={null}
        title="Dune"
        className="aspect-[2/3] w-12 object-cover rounded-md"
      />,
    );

    const fallback = screen.getByLabelText('Dune');
    expect(fallback.className).toContain('aspect-[2/3]');
  });

  it('Test 2: fallback initial uses font-semibold text-xl, not font-bold (D-08)', () => {
    render(<BookCoverImage coverId={null} title="Dune" />);

    const fallback = screen.getByLabelText('Dune');
    expect(fallback.className).toContain('font-semibold');
    expect(fallback.className).toContain('text-xl');
    expect(fallback.className).not.toContain('font-bold');
  });
});
