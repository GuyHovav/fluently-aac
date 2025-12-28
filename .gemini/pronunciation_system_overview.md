# Scalable Pronunciation Correction System for Hebrew TTS

## Overview

This document describes the implementation of a comprehensive pronunciation correction system for Hebrew text-to-speech (TTS) in the MyAAC application. The system addresses the common problem of Hebrew words being mispronounced due to missing vowel points (nikud).

## The Problem

Hebrew text is typically written without vowel points (nikud), which creates ambiguity. For example:
- רעב can be pronounced as either:
  - רָעֵב (ra'ev) = "hungry" ✓
  - רָעָב (ra'av) = "hunger" ✗

The Android TTS engine must guess the pronunciation, and often gets it wrong.

## The Solution

We've implemented a **3-tier pronunciation correction system**:

### Tier 1: Built-in Dictionary
A curated dictionary of commonly mispronounced Hebrew words with nikud, automatically applied to all Hebrew speech.

**Location**: `PronunciationDictionary.kt`

**Includes**:
- Food & Eating: רעב, צמא, אוכל, שותה
- Actions: רוצה, צריך, הולך, בא, עושה
- Feelings: שמח, עצוב, כועס, פוחד
- Common words: כן, לא, עכשיו, בבקשה, תודה
- Family: אמא, אבא, אח, אחות
- Time: בוקר, ערב, לילה, יום
- And more...

### Tier 2: User-Editable Custom Dictionary
Caregivers can add their own pronunciation corrections through the UI for words specific to their needs.

**Features**:
- Add custom word corrections
- Remove custom corrections
- Persistent storage (survives app restarts)
- Takes priority over built-in dictionary

### Tier 3: Future Enhancement (Not Implemented Yet)
Optional integration with automatic nikud APIs like:
- UNIKUD (open-source deep learning model)
- Nakdan Mahir by Dicta
- NiqqudGPT

## Architecture

### Core Components

1. **PronunciationDictionary** (`data/pronunciation/PronunciationDictionary.kt`)
   - Contains built-in Hebrew word corrections
   - Manages custom user corrections
   - Applies corrections using word-boundary regex

2. **PronunciationRepository** (`data/repository/PronunciationRepository.kt`)
   - Handles persistence of custom pronunciations
   - Uses SharedPreferences with JSON serialization
   - Provides methods to add/remove/clear custom pronunciations

3. **PronunciationManagementScreen** (`ui/PronunciationManagementScreen.kt`)
   - UI for managing custom pronunciations
   - Shows both built-in and custom corrections
   - Allows caregivers to add new corrections
   - Delete custom corrections (built-in are read-only)

4. **Integration in MainActivity**
   - Automatically applies corrections before TTS speaks
   - Works transparently for all Hebrew speech

## How It Works

### Automatic Correction Flow

```
User presses button → Sentence formed → speak() called
                                           ↓
                              pronunciationRepository.dictionary.applyCorrections()
                                           ↓
                              1. Apply custom dictionary (first)
                              2. Apply built-in dictionary
                                           ↓
                              Corrected text → TTS speaks
```

### Adding Custom Pronunciations

1. Open Settings
2. If language is Hebrew, "Pronunciation Corrections" appears
3. Tap "Manage Custom Pronunciations"
4. Tap the + button
5. Enter:
   - Original word (without nikud): `רעב`
   - Corrected word (with nikud): `רָעֵב`
6. Tap "Add"
7. Pronunciation is immediately saved and used

## Benefits

### ✅ Scalable
- No need to edit code for each new word
- Caregivers can add corrections themselves
- Built-in dictionary covers common cases

### ✅ Flexible
- Custom corrections take priority
- Can override built-in corrections if needed
- Works for any Hebrew word

### ✅ User-Friendly
- Simple UI for non-technical caregivers
- Clear examples showing nikud format
- Immediate feedback

### ✅ Maintainable
- Centralized correction logic
- Easy to expand built-in dictionary
- Clean separation of concerns

## Usage Examples

### For Developers: Adding Built-in Words

Edit `PronunciationDictionary.kt`:

```kotlin
private val builtInHebrewDictionary = mapOf(
    // ... existing entries ...
    "חדש" to "חָדָשׁ",  // new
    "ישן" to "יָשָׁן"   // old
)
```

### For Caregivers: Adding Custom Words

1. In the app, go to Settings
2. Tap "Manage Custom Pronunciations"
3. Tap the + button
4. Add your word pair
5. Done! The word will now be pronounced correctly

## Testing

To test the pronunciation corrections:

1. Switch app language to Hebrew
2. Create a button with the word "רעב"
3. Press the button and listen
4. It should now say "ra'ev" (hungry) instead of "ra'av" (hunger)

## Future Enhancements

### Phase 1 (Current) ✅
- [x] Built-in dictionary
- [x] Custom user dictionary
- [x] Persistence
- [x] UI for management

### Phase 2 (Future)
- [ ] Import/export pronunciation dictionaries
- [ ] Share dictionaries between users
- [ ] Bulk import from CSV/JSON
- [ ] Test pronunciation before saving

### Phase 3 (Future)
- [ ] Integration with automatic nikud API
- [ ] AI-powered pronunciation suggestions
- [ ] Context-aware pronunciation (same word, different meanings)
- [ ] Support for other languages with similar issues

## Technical Details

### Word Boundary Regex
```kotlin
val regex = Regex("(?<!\\p{L})${Regex.escape(original)}(?!\\p{L})")
```
- Ensures only whole words are replaced
- Prevents partial matches (e.g., רעב in ברעב)

### Priority System
1. Custom dictionary is checked first
2. Then built-in dictionary
3. Original text used if no match

### Persistence Format
JSON stored in SharedPreferences:
```json
{
  "רעב": "רָעֵב",
  "צמא": "צָמֵא"
}
```

## Files Modified/Created

### Created
- `app/src/main/java/com/example/myaac/data/pronunciation/PronunciationDictionary.kt`
- `app/src/main/java/com/example/myaac/data/repository/PronunciationRepository.kt`
- `app/src/main/java/com/example/myaac/ui/PronunciationManagementScreen.kt`

### Modified
- `app/src/main/java/com/example/myaac/MyAacApplication.kt` - Added pronunciationRepository
- `app/src/main/java/com/example/myaac/MainActivity.kt` - Integrated pronunciation corrections
- `app/src/main/java/com/example/myaac/ui/SettingsScreen.kt` - Added navigation to pronunciation management

## Summary

The pronunciation correction system provides a scalable, flexible solution to the רעב problem and all similar Hebrew pronunciation issues. With 40+ built-in corrections and a user-friendly UI for adding custom ones, it empowers both developers and caregivers to ensure accurate speech output.
