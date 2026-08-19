import { useEffect, useState } from 'react';

// React port of app/src/main/java/com/example/myaac/util/WindowSizeUtils.kt.
//
// Compose's `rememberWindowSizeClass()` reads `LocalConfiguration.screenWidthDp/screenHeightDp`.
// The closest web equivalent is `window.innerWidth/innerHeight` in CSS pixels: Capacitor's
// Android WebView renders with the standard `<meta name="viewport" content="width=device-width">`
// behavior, so CSS px there is already device-independent-pixel-equivalent, the same normalization
// Compose's `dp` unit provides -- i.e. this is a reasonable stand-in, not an exact bit-for-bit
// match (WebView CSS px and Compose dp can drift by fractions of a pixel depending on the WebView's
// own zoom/DPI handling, which doesn't matter at the breakpoint granularity used here).

export type WindowWidthSizeClass = 'compact' | 'medium' | 'expanded';
export type WindowHeightSizeClass = 'compact' | 'medium' | 'expanded';

export interface WindowSizeClass {
  widthClass: WindowWidthSizeClass;
  heightClass: WindowHeightSizeClass;
  width: number;
  height: number;
}

function classify(width: number, height: number): WindowSizeClass {
  const widthClass: WindowWidthSizeClass = width < 600 ? 'compact' : width < 840 ? 'medium' : 'expanded';
  const heightClass: WindowHeightSizeClass = height < 480 ? 'compact' : height < 900 ? 'medium' : 'expanded';
  return { widthClass, heightClass, width, height };
}

/** Port of `rememberWindowSizeClass()`. Re-classifies on window resize (orientation change, foldables, etc). */
export function useWindowSize(): WindowSizeClass {
  const [size, setSize] = useState<WindowSizeClass>(() => classify(window.innerWidth, window.innerHeight));

  useEffect(() => {
    const onResize = () => setSize(classify(window.innerWidth, window.innerHeight));
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  return size;
}

/** Port of `WindowSizeClass.getRecommendedColumns(baseColumns)`. */
export function getRecommendedColumns(baseColumns: number, sizeClass: WindowSizeClass): number {
  const isPortrait = sizeClass.height > sizeClass.width;
  switch (sizeClass.widthClass) {
    case 'compact':
      return baseColumns;
    case 'medium':
      return isPortrait ? baseColumns + 1 : baseColumns + 2;
    case 'expanded':
      return isPortrait ? baseColumns + 2 : baseColumns + 3;
  }
}
