import type { AacButton } from './AacButton';

// Mirrors com.example.myaac.model.Board (Room @Entity(tableName = "boards")).
//
// In Room, `buttons: List<AacButton>` is persisted as a single JSON TEXT column via
// Converters.fromButtonList/toButtonList (Gson). In Dexie there is no equivalent of a
// TypeConverter-flattened column, so `buttons` is kept embedded as a plain array
// property on the board row and IndexedDB's structured-clone algorithm stores it
// natively (no JSON (de)serialization step needed) — see web/src/data for the schema
// and rationale for keeping buttons embedded rather than a separate indexed table.
export interface Board {
  id: string;
  name: string;
  rows: number;
  columns: number;
  buttons: AacButton[];
  iconPath: string | null;
  backgroundImagePath: string | null;
}

/** Defaults mirroring the Kotlin data class constructor (rows/columns = 4, buttons = []). */
export function createBoard(
  params: Pick<Board, 'id' | 'name'> & Partial<Omit<Board, 'id' | 'name'>>,
): Board {
  return {
    rows: 4,
    columns: 4,
    buttons: [],
    iconPath: null,
    backgroundImagePath: null,
    ...params,
  };
}
