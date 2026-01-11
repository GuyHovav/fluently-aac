# Symbol Vendor Fallback Implementation

## Overview
Implemented a robust fallback mechanism for symbol retrieval. When a symbol is not found in the primary vendor (e.g., ARASAAC), the system automatically searches the secondary vendor (e.g., Mulberry).

## Features
1. **Automatic Fallback**: Seamlessly switches to the next vendor if the first one yields no results.
2. **Universal Coverage**: Applied to:
   - **Manual Search**: In the Symbol Search Dialog.
   - **Board Population**: When generating Magic Boards, Quick Boards from photos, and filling symbol gaps.
3. **User Preference Respected**: Fallback order depends on the selected primary library (e.g., if Mulberry is selected, ARASAAC is the fallback).

## Changes
- **`CompositeSymbolService.kt`**: New service handling the fallback logic.
- **`BoardViewModel.kt`**: Updated to use `CompositeSymbolService` for all internal symbol lookups (AI board generation, etc.).
- **`SymbolSearchDialog.kt`**: Updated UI to reflect fallback capability (e.g., "Arasaac → Mulberry").

## Verification
- Unit tests added in `CompositeSymbolServiceTest` verifying fallback and error handling scenarios.
