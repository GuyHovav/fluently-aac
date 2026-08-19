import { liveQuery } from 'dexie';
import type { Board } from '../models';
import { db } from './db';

// Mirrors app/src/main/java/com/example/myaac/data/local/BoardDao.kt.
// Dexie's `liveQuery` stands in for Room's `Flow<...>` return types.

/** Mirrors `BoardDao.getAllBoards(): Flow<List<Board>>`. */
export function getAllBoards$() {
  return liveQuery(() => db.boards.toArray());
}

/** Mirrors `BoardDao.getAllBoardsData(): List<Board>` (one-shot, non-reactive). */
export async function getAllBoardsData(): Promise<Board[]> {
  return db.boards.toArray();
}

/** Mirrors `BoardDao.getBoardById(boardId): Board?`. */
export async function getBoardById(boardId: string): Promise<Board | undefined> {
  return db.boards.get(boardId);
}

/** Mirrors `BoardDao.getBoardasFlow(boardId): Flow<Board?>`. */
export function getBoardById$(boardId: string) {
  return liveQuery(() => db.boards.get(boardId));
}

/** Mirrors `BoardDao.insertBoard(board)` (`OnConflictStrategy.REPLACE`, i.e. an upsert). */
export async function saveBoard(board: Board): Promise<void> {
  await db.boards.put(board);
}

/** Mirrors `BoardDao.deleteBoard(boardId)`. */
export async function deleteBoard(boardId: string): Promise<void> {
  await db.boards.delete(boardId);
}
