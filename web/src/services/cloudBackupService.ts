// Port of data/repository/CloudRepository.kt's Firestore + Storage board backup/restore logic,
// against the Firebase Web SDK handles from firebaseConfig.ts.
//
// Firestore/Storage document shape is unchanged from the Kotlin source:
//   users/{userId}/boards/{boardId}  <- one Firestore document per Board (Board.copy(...) with
//                                       image paths swapped to Storage download URLs)
//   users/{userId}/images/{filename} <- one Storage object per uploaded local image
//
// uploadImageIfLocal's local-path handling differs from the Kotlin source out of necessity: the
// Kotlin version reads a `file://`/absolute filesystem path off disk and re-uses its original
// filename. In the browser, camera/gallery-captured button images are `data:` URL strings (see
// AacButton.iconPath's doc comment in web/src/models/AacButton.ts), not filesystem paths, so
// there is no original filename to reuse -- a deterministic name is derived instead from the
// owning board/button id (see filenameHint below), which also means re-running a backup
// overwrites the same Storage object rather than accumulating duplicates, matching the Kotlin
// comment "For now, overwrite or simple upload."
//
// Ported surface, matching exactly what CloudRepository.kt + BoardViewModel.backupToCloud /
// restoreFromCloud expose (nothing extra):
//   - backupBoard(board, userId)   <- CloudRepository.backupBoard
//   - backupBoards(userId)         <- BoardViewModel.backupToCloud's loop-over-all-boards
//   - restoreBoards(userId)        <- CloudRepository.restoreBoards + BoardViewModel's
//                                      save-each-into-local-repo step
// CloudRepository.kt has no getLastBackupTime/delete-backup methods, so none are added here.

import { collection, doc, getDocs, setDoc } from 'firebase/firestore';
import { getDownloadURL, ref as storageRef, uploadString } from 'firebase/storage';

import type { Board } from '../models';
import { getAllBoardsData, saveBoard } from '../data/boardRepository';
import { firestore, storage } from './firebaseConfig';

const IMAGE_EXTENSION_BY_MIME_TYPE: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'image/gif': 'gif',
};

const DATA_URL_RE = /^data:([^;,]+)(;base64)?,/;

/**
 * Mirrors `CloudRepository.uploadImageIfLocal`: `null` passes through, an already-remote
 * `http(s)://` URL (e.g. a symbol-search result) passes through unchanged, and a local image is
 * uploaded to Storage with the resulting download URL returned in its place.
 *
 * "Local" here means a `data:` URL (how camera/gallery captures are represented on the web --
 * see the module doc comment above); any other unrecognized scheme is left as-is since there is
 * nothing in a browser context that could resolve it.
 */
async function uploadImageIfLocal(
  path: string | null,
  userId: string,
  filenameHint: string,
): Promise<string | null> {
  if (path == null) return null;
  if (path.startsWith('http')) return path; // Already a generic URL (or cloud URL).
  if (!path.startsWith('data:')) return path; // Not a data URL -- nothing uploadable here.

  const match = DATA_URL_RE.exec(path);
  const mimeType = match?.[1] ?? 'image/png';
  const extension = IMAGE_EXTENSION_BY_MIME_TYPE[mimeType] ?? 'png';

  const imageRef = storageRef(storage, `users/${userId}/images/${filenameHint}.${extension}`);
  await uploadString(imageRef, path, 'data_url');
  return getDownloadURL(imageRef);
}

/** Mirrors `CloudRepository.backupBoard(board, userId)`. */
export async function backupBoard(board: Board, userId: string): Promise<void> {
  // Upload Board Images
  const cloudIconPath = await uploadImageIfLocal(board.iconPath, userId, `${board.id}_icon`);
  const cloudBgPath = await uploadImageIfLocal(board.backgroundImagePath, userId, `${board.id}_background`);

  // Upload Button Images
  const cloudButtons = await Promise.all(
    board.buttons.map(async (button) => ({
      ...button,
      iconPath: await uploadImageIfLocal(button.iconPath, userId, `${board.id}_${button.id}`),
    })),
  );

  const cloudBoard: Board = {
    ...board,
    iconPath: cloudIconPath,
    backgroundImagePath: cloudBgPath,
    buttons: cloudButtons,
  };

  // Save to Firestore
  await setDoc(doc(firestore, 'users', userId, 'boards', board.id), cloudBoard);
}

export interface BackupSummary {
  successCount: number;
  totalCount: number;
}

/** Mirrors `BoardViewModel.backupToCloud`: back up every local board, one at a time. */
export async function backupBoards(userId: string): Promise<BackupSummary> {
  const boards = await getAllBoardsData();
  let successCount = 0;
  for (const board of boards) {
    await backupBoard(board, userId);
    successCount++;
  }
  return { successCount, totalCount: boards.length };
}

/**
 * Mirrors `CloudRepository.restoreBoards` plus `BoardViewModel.restoreFromCloud`'s
 * save-each-into-the-local-repo step: fetches every board document under the user's Firestore
 * path and upserts each into the local Dexie store via `boardRepository.saveBoard`.
 */
export async function restoreBoards(userId: string): Promise<Board[]> {
  const snapshot = await getDocs(collection(firestore, 'users', userId, 'boards'));
  const boards = snapshot.docs.map((docSnap) => docSnap.data() as Board);

  for (const board of boards) {
    await saveBoard(board);
  }

  return boards;
}
