# Cloud Backup & User Profile Implementation Plan

## 1. Overview
This plan outlines the implementation of cloud backup and user profiles for the MyAAC application. The goal is to allow users to sign in, back up their custom boards (including custom images) and settings to the cloud, and restore them on another device.

## 2. Technology Stack
-   **Authentication**: Firebase Authentication (Google Sign-In, Email/Password).
-   **Database**: Firebase Cloud Firestore (NoSQL DB for storing board metadata, buttons, and user profiles).
-   **File Storage**: Firebase Cloud Storage (for storing user-uploaded images/icons).
-   **Android SDK**:
    -   `com.google.firebase:firebase-auth-ktx`
    -   `com.google.firebase:firebase-firestore-ktx`
    -   `com.google.firebase:firebase-storage-ktx`
    -   `com.google.android.gms:play-services-auth` (for Google Sign-In)

## 3. Data Models

### 3.1. Firestore Structure
```text
users/
  {userId}/
    profile/
      {docId} (e.g., "info")
        email: string
        displayName: string
        createdAt: timestamp
        subscriptionStatus: string (optional)
        
    boards/
      {boardId}/
        name: string
        rows: int
        columns: int
        iconPath: string (url)
        backgroundImagePath: string (url)
        buttons: string (JSON blob) or subcollection?
            -> RECOMMENDATION: Store 'buttons' as a JSON string or map within the document if size < 1MB to keep it simple and aligned with local Room DB structure. If users have massive boards, subcollections might be better, but simpler is better for v1.
            
    settings/
        preferences:
           defaultVoice: string
           ...
```

## 4. Implementation Steps

### Phase 1: dependencies & Firebase Setup
1.  **Project Setup**: Create a Firebase project in the Firebase Console.
2.  **Android App Registration**: Register the package `com.example.myaac`.
3.  **Gradle Dependencies**: Add the Google Services Classpath and Firebase BOM/dependencies to `build.gradle.kts`. Not forgetting `google-services.json`.

### Phase 2: Authentication (User Profile)
1.  **Auth Repository**: Create `AuthRepository` to handle sign-in/sign-out logic.
2.  **UI**:
    -   Create a `LoginScreen` (Compose).
    -   Add "Sign In" button to the Settings/Admin menu.
    -   Display User Profile info (Avatar, Name) when logged in.
3.  **State Management**: Update `MainViewModel` or a global `UserViewModel` to hold the current `User` state.

### Phase 3: Backup Logic (Upload)
1.  **Backup Repository**: Create `CloudRepository`.
2.  **Image Handling**:
    -   Scan all boards for local image paths (`file://...`).
    -   **Hash Check**: (Optional optimization) Check if file already exists in Cloud Storage to avoid re-uploading.
    -   Upload local images to `users/{userId}/images/{filename}`.
    -   Get Download URL.
    -   **Path Replacement**: Create a *copy* of the Board object where local paths are replaced with Cloud Storage URLs.
3.  **Data Upload**:
    -   Write the modified Board object (with Cloud URLs) to Firestore `users/{userId}/boards/{boardId}`.

### Phase 4: Restore Logic (Download)
1.  **Fetch Data**: Retrieve all documents from `users/{userId}/boards`.
2.  **Image Handling**:
    -   The app can load images directly from valid URLs (Coil already supports this).
    -   *Optionally* for offline support: Download the images to local storage and update the Board object to point to local paths again. **Decision: Enable Offline Caching via Coil first, full offline restore later.**
3.  **Database Insert**: Insert the fetched Boards into the local Room database. Handle ID conflicts (Overwite? Merge? Prompt user?).

## 5. UI/UX Considerations
-   **Sync Status**: Show a spinner or progress bar during backup/restore.
-   **Conflict Resolution**: If a board with the same ID exists locally and remotely, ask the user (or default to "Server Wins" or "Last Modified Wins").
-   **Storage Limits**: Be aware of Firebase Spark plan limits (1GB storage).

## 6. Next Steps Action Items
1.  [ ] User validates this plan.
2.  [ ] Setup Firebase Project (User needs to provide `google-services.json`).
3.  [ ] Add dependencies.
4.  [ ] Create Auth UI.
