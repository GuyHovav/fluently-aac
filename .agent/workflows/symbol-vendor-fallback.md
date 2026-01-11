---
description: Symbol vendor fallback implementation
---

# Symbol Vendor Fallback Implementation

## Overview
Implement a fallback mechanism so that if a symbol isn't found on one vendor, the system automatically searches on another vendor.

## Implementation Steps

### 1. Create a Composite Symbol Service
Create a new service class that wraps multiple symbol vendors and implements the fallback logic:
- Try the primary vendor first
- If no results are found, try the secondary vendor
- Return combined results or results from whichever vendor succeeded

### 2. Update SymbolSearchDialog
Modify the `SymbolSearchDialog` to use the new composite service instead of selecting a single service.

### 3. Add Configuration (Optional)
Consider adding a preference for:
- Primary vendor preference
- Whether to enable fallback
- Whether to combine results from both vendors

## Technical Details

### New Class: `CompositeSymbolService`
- Implements `SymbolService` interface
- Takes a list of symbol services in priority order
- Tries each service until results are found
- Option to combine results from all services

### Modified Files
- `SymbolSearchDialog.kt` - Use composite service
- New file: `CompositeSymbolService.kt` - Fallback logic

## Benefits
- Better symbol coverage
- Improved user experience (fewer "no results" scenarios)
- Transparent to the user
- Maintains existing service implementations
