// Port of data/remote/GeminiService.kt — near-mechanical translation.
//
// The Kotlin source used the `com.google.ai.client.generativeai` (Google AI Android SDK) client
// library. There's no equivalent first-party JS SDK dependency added here per the "use fetch,
// no need for axios" instruction, so this talks to the Gemini REST API directly:
//   POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=API_KEY
// with the same model ("gemini-2.5-flash") and safety settings (BLOCK_ONLY_HIGH for all four
// harm categories) as the original two GenerativeModel instances (visionModel/textModel — the
// Kotlin source used two separate instances with identical config "to ensure stability"; here a
// single generateContent() helper is used for both, since nothing about the JS fetch call is
// stateful the way a live model handle is).

import type { PhraseCacheService } from './phraseCacheService';

const MODEL_NAME = 'gemini-2.5-flash';
const API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

// Vite convention: env vars exposed to client code must be prefixed VITE_.
// See web/.env.example for the expected keys.
function getApiKey(): string {
  return import.meta.env.VITE_GEMINI_API_KEY ?? '';
}

const SAFETY_SETTINGS = [
  { category: 'HARM_CATEGORY_HARASSMENT', threshold: 'BLOCK_ONLY_HIGH' },
  { category: 'HARM_CATEGORY_HATE_SPEECH', threshold: 'BLOCK_ONLY_HIGH' },
  { category: 'HARM_CATEGORY_SEXUALLY_EXPLICIT', threshold: 'BLOCK_ONLY_HIGH' },
  { category: 'HARM_CATEGORY_DANGEROUS_CONTENT', threshold: 'BLOCK_ONLY_HIGH' },
];

/** An image to send to the vision model. Analogous to the `Bitmap` parameter in the Kotlin source. */
export interface GeminiImageInput {
  /** Raw base64 image data, no "data:image/...;base64," prefix. */
  base64Data: string;
  mimeType: string;
}

interface GeminiPart {
  text?: string;
  inline_data?: { mime_type: string; data: string };
}

interface GeminiCandidate {
  content?: { parts?: { text?: string }[] };
  finishReason?: string;
}

interface GeminiGenerateContentResponse {
  candidates?: GeminiCandidate[];
  promptFeedback?: { blockReason?: string };
}

/** Mirrors the `response.text` convenience getter on the Kotlin SDK's GenerateContentResponse. */
function getResponseText(response: GeminiGenerateContentResponse): string | null {
  const parts = response.candidates?.[0]?.content?.parts;
  if (!parts || parts.length === 0) return null;
  const text = parts
    .map((p) => p.text ?? '')
    .join('')
    .trim();
  return text.length > 0 ? text : null;
}

async function callGemini(parts: GeminiPart[]): Promise<GeminiGenerateContentResponse> {
  const apiKey = getApiKey();
  const url = `${API_BASE}/${MODEL_NAME}:generateContent?key=${encodeURIComponent(apiKey)}`;
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      contents: [{ parts }],
      safetySettings: SAFETY_SETTINGS,
    }),
  });

  if (!response.ok) {
    const err = new Error(`Gemini API HTTP ${response.status}`);
    (err as Error & { status?: number }).status = response.status;
    throw err;
  }

  return (await response.json()) as GeminiGenerateContentResponse;
}

async function generateTextContent(prompt: string): Promise<GeminiGenerateContentResponse> {
  return callGemini([{ text: prompt }]);
}

/**
 * Low-level "send this prompt, get the text back" helper, exported for morphologyService.ts
 * (see web/src/services/morphologyService.ts), which per the migration plan's "Replacing
 * SimpleNLG" section makes its own single-purpose Gemini prompt call rather than being a method
 * on GeminiService itself (GeminiService's public surface here is a faithful 1:1 port of
 * GeminiService.kt's methods; this shared helper avoids duplicating the fetch/parsing logic).
 */
export async function generateGeminiText(prompt: string): Promise<string | null> {
  const response = await generateTextContent(prompt);
  return getResponseText(response);
}

/**
 * Downscale an image so neither dimension exceeds maxDimension, preserving aspect ratio.
 * Port of GeminiService.kt's private scaleBitmap(). Uses the Canvas API since this runs in a
 * Capacitor WebView (browser context), not a JVM Bitmap.
 */
async function scaleImageIfNeeded(
  image: GeminiImageInput,
  maxDimension: number = 2048,
): Promise<GeminiImageInput> {
  const dataUrl = `data:${image.mimeType};base64,${image.base64Data}`;

  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const el = new Image();
    el.onload = () => resolve(el);
    el.onerror = () => reject(new Error('GeminiService: failed to decode image for scaling'));
    el.src = dataUrl;
  });

  const originalWidth = img.naturalWidth;
  const originalHeight = img.naturalHeight;

  if (originalWidth <= maxDimension && originalHeight <= maxDimension) {
    return image;
  }

  let newWidth = originalWidth;
  let newHeight = originalHeight;
  if (originalWidth > originalHeight) {
    newWidth = maxDimension;
    newHeight = Math.round((maxDimension / originalWidth) * originalHeight);
  } else {
    newHeight = maxDimension;
    newWidth = Math.round((maxDimension / originalHeight) * originalWidth);
  }

  const canvas = document.createElement('canvas');
  canvas.width = newWidth;
  canvas.height = newHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) return image; // Fallback: no canvas support, send original.
  ctx.drawImage(img, 0, 0, newWidth, newHeight);

  const outMimeType = image.mimeType === 'image/png' ? 'image/png' : 'image/jpeg';
  const scaledDataUrl = canvas.toDataURL(outMimeType, 0.92);
  const base64Data = scaledDataUrl.substring(scaledDataUrl.indexOf(',') + 1);

  return { base64Data, mimeType: outMimeType };
}

function imagePart(image: GeminiImageInput): GeminiPart {
  return { inline_data: { mime_type: image.mimeType, data: image.base64Data } };
}

// ===== Agent Actions (mirrors GeminiService.AgentAction sealed class) =====

export type AgentAction =
  | { type: 'CreateBoard'; topic: string; negativeConstraints: string | null }
  | { type: 'AnswerQuestion'; question: string }
  | { type: 'Unknown'; reason: string };

function languageNameFor(languageCode: string): string {
  return languageCode === 'iw' || languageCode === 'he' ? 'Hebrew' : 'English';
}

export class GeminiService {
  /**
   * Identify the main object in an image and its bounding box.
   * Returns { name, boundingBox } where boundingBox is [ymin, xmin, ymax, xmax] as 0-1 fractions,
   * or null if it couldn't be parsed. Mirrors Kotlin's `Pair<String, FloatArray?>`.
   */
  async identifyItem(image: GeminiImageInput): Promise<{ name: string; boundingBox: number[] | null }> {
    const prompt = `Identify the main object in this image and provide its bounding box location.

CRITICAL: You MUST respond in this EXACT format:
ObjectName | ymin, xmin, ymax, xmax

Where:
- ObjectName: Single simple word (e.g., Apple, Dog, Chair, Person)
- ymin, xmin, ymax, xmax: Bounding box coordinates as percentages (0-100)
  * ymin: top edge (% from top)
  * xmin: left edge (% from left)
  * ymax: bottom edge (% from top)
  * xmax: right edge (% from left)

Examples:
- "Apple | 20, 30, 80, 70" (apple in center-right)
- "Dog | 10, 5, 90, 95" (dog filling most of image)
- "Chair | 40, 25, 85, 75" (chair in lower portion)

Do NOT identify specific individuals.
Do NOT add any extra text or explanation.
ALWAYS include the pipe | and coordinates.`;

    const scaledImage = await scaleImageIfNeeded(image);

    try {
      const response = await callGemini([imagePart(scaledImage), { text: prompt }]);

      const finishReason = response.candidates?.[0]?.finishReason;
      const safety = response.promptFeedback?.blockReason;

      const text = getResponseText(response);
      if (text == null) {
        if (safety) return { name: `Err: Blocked (${safety})`, boundingBox: null };
        if (finishReason) return { name: `Err: End (${finishReason})`, boundingBox: null };
        return { name: 'Err: Empty/Null Response', boundingBox: null };
      }

      // Parse "Name | 10, 20, 90, 80" or "Name|10,20,90,80"
      if (text.includes('|')) {
        const parts = text.split('|');
        const name = parts[0].trim();
        const coordsString = (parts[1] ?? '').trim();

        // Remove brackets and parse coordinates
        const cleanCoords = coordsString.replace(/\[|\]| /g, '');
        const coords = cleanCoords
          .split(',')
          .map((s) => {
            const n = Number.parseFloat(s.trim());
            return Number.isNaN(n) ? null : n / 100;
          })
          .filter((n): n is number => n !== null);

        if (coords.length === 4) {
          return { name, boundingBox: coords };
        }
        return { name, boundingBox: null };
      }

      // Fallback: If it just returned a name without coordinates
      return { name: text, boundingBox: null };
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Unknown Error';
      if (msg.includes('400')) return { name: 'Err: 400 Bad Request', boundingBox: null };
      if (msg.includes('401')) return { name: 'Err: 401 Unauthorized', boundingBox: null };
      return { name: `Err: ${msg}`, boundingBox: null };
    }
  }

  /**
   * Identify 8-12 prominent objects in a photo, for AAC board generation from a scene photo.
   * Returns a list of { name, boundingBox } (boundingBox always present here, unlike identifyItem).
   */
  async identifyMultipleItems(image: GeminiImageInput): Promise<{ name: string; boundingBox: number[] }[]> {
    const prompt = `You are analyzing a photo to create an AAC (Augmentative and Alternative Communication) board.

Identify 8-12 of the MOST IMPORTANT and CLEARLY VISIBLE objects that a person would want to communicate about.
Focus on:
- Objects that are large and clearly visible
- Interactive items (things you can touch, use, or eat)
- Common household items, furniture, appliances, food, toys, people
- Objects that take up significant space in the image

AVOID:
- Small or partially visible objects
- Background details
- Decorative items
- Ambiguous objects

For EACH object, provide:
1. A simple, single-word label (e.g., "Refrigerator" not "White Refrigerator")
2. Accurate bounding box coordinates

CRITICAL FORMAT - One object per line:
ObjectName | ymin, xmin, ymax, xmax

Where coordinates are percentages (0-100):
- ymin: distance from TOP edge to object's TOP
- xmin: distance from LEFT edge to object's LEFT
- ymax: distance from TOP edge to object's BOTTOM
- xmax: distance from LEFT edge to object's RIGHT

Example:
Refrigerator | 10, 5, 85, 45
Table | 60, 30, 95, 90

Respond ONLY with the object list. No explanations.`;

    // Use higher resolution for better recognition
    const scaledImage = await scaleImageIfNeeded(image, 2048);

    try {
      const response = await callGemini([imagePart(scaledImage), { text: prompt }]);
      const text = getResponseText(response);
      if (text == null) return [];

      return text
        .split('\n')
        .filter((line) => line.trim().length > 0 && line.includes('|'))
        .map((line) => {
          try {
            const parts = line.split('|');
            if (parts.length !== 2) return null;

            const name = parts[0].trim();
            if (name.length === 0) return null;

            const coordsString = parts[1].trim();
            const cleanCoords = coordsString.replace(/\[|\]| /g, '');
            const coords = cleanCoords
              .split(',')
              .map((s) => Number.parseFloat(s.trim()))
              .filter((n) => !Number.isNaN(n))
              .map((n) => n / 100);

            if (
              coords.length === 4 &&
              coords[0] >= 0 &&
              coords[0] <= 1 &&
              coords[1] >= 0 &&
              coords[1] <= 1 &&
              coords[2] >= 0 &&
              coords[2] <= 1 &&
              coords[3] >= 0 &&
              coords[3] <= 1 &&
              coords[2] > coords[0] &&
              coords[3] > coords[1]
            ) {
              return { name, boundingBox: coords };
            }
            return null;
          } catch {
            return null;
          }
        })
        .filter((r): r is { name: string; boundingBox: number[] } => r !== null);
    } catch (e) {
      console.error('GeminiService: Error in identifyMultipleItems', e);
      return [];
    }
  }

  /** Predict the next N words the user might want to say, given recent context. */
  async predictNextWords(
    context: string[],
    count: number = 5,
    languageCode: string = 'en',
    topic?: string | null,
  ): Promise<string[]> {
    const languageName = languageNameFor(languageCode);
    const contextText = context.slice(-5).join(' ');
    const topicClause =
      topic != null && topic.trim().length > 0
        ? `The current conversation topic/board is: '${topic}'. Prioritize words related to this topic.`
        : '';

    const prompt = `You are helping with word prediction for an AAC (Augmentative and Alternative Communication) app.

The user has typed: "${contextText}"
${topicClause}

Predict the next ${count} words they might want to say in ${languageName}.
Focus on:
- Common conversational words
- Contextually relevant words (especially related to '${topic}' if provided)
- Simple, everyday vocabulary

Return ONLY the words, separated by commas.
Example: want, need, like, have, go`;

    try {
      const response = await generateTextContent(prompt);
      const text = getResponseText(response);
      if (text == null) return [];
      return text
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
        .slice(0, count);
    } catch (e) {
      console.error('GeminiService: Error in predictNextWords', e);
      return [];
    }
  }

  /** Classify a free-text agent command into CREATE_BOARD / ANSWER_QUESTION / UNKNOWN. */
  async parseAgentCommand(query: string): Promise<AgentAction> {
    const prompt = `You are an AI assistant for an AAC app. Analyze the user's request and classify it into one of these actions:

1. CREATE_BOARD: User wants to create a new symbol board.
   - Extract 'topic' (e.g., "nightclub", "food", "park")
   - Extract 'negative_constraints' if any (e.g., "no alcohol", "without meat"). Return "null" if none.

2. ANSWER_QUESTION: User asks a question about the app or general help.
   - Extract the 'question'.

3. UNKNOWN: If the request is unclear or unrelated.

Response Format:
TYPE | param1 | param2

Examples:
"Create a board about space" -> CREATE_BOARD | Space | null
"Make a board for a bar but no beer symbols" -> CREATE_BOARD | Bar | No beer symbols
"How do I use this app?" -> ANSWER_QUESTION | How do I use this app? | null
"Hello" -> ANSWER_QUESTION | Hello | null

Input: "${query}"

Return ONLY the formatted string.`;

    try {
      const response = await generateTextContent(prompt);
      const text = getResponseText(response);
      if (text == null) return { type: 'Unknown', reason: 'Empty response' };

      const parts = text.split('|').map((s) => s.trim());
      switch (parts[0].toUpperCase()) {
        case 'CREATE_BOARD': {
          const topic = parts[1] ?? 'General';
          const rawConstraints = parts[2];
          const constraints =
            rawConstraints != null && rawConstraints !== 'null' && rawConstraints !== 'None'
              ? rawConstraints
              : null;
          return { type: 'CreateBoard', topic, negativeConstraints: constraints };
        }
        case 'ANSWER_QUESTION': {
          const question = parts[1] ?? query;
          return { type: 'AnswerQuestion', question };
        }
        default:
          return { type: 'Unknown', reason: text };
      }
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      return { type: 'Unknown', reason: `Error: ${message}` };
    }
  }

  /** Answer a free-text question about the app, using a short embedded knowledge base. */
  async answerQuestion(question: string): Promise<string> {
    const knowledgeBase = `You are 'Fluently', a helpful AI assistant built into an AAC (Augmentative and Alternative Communication) app.

App Features:
- Create Boards: Users can create custom boards by typing a topic (e.g., "Create board for school").
- Magic Boards: The app uses AI to generate relevant vocabulary.
- Photo Boards: Users can take a photo to generate a board based on the scene (using the camera icon).
- Text-to-Speech: Tapping icons speaks the word.
- Validation: You can validate the query against app capabilities.

Style:
- Be concise, helpful, and friendly.
- Keep answers short (under 3 sentences) as users may have reading difficulties.`;

    const prompt = `${knowledgeBase}

User Question: "${question}"

Answer:`;

    try {
      const response = await generateTextContent(prompt);
      return getResponseText(response) ?? "I couldn't generate an answer.";
    } catch {
      return "Sorry, I'm having trouble connecting right now.";
    }
  }

  /** Generate an initial vocabulary list for a new board topic. */
  async generateBoard(
    topic: string,
    languageCode: string = 'en',
    count: number = 16,
    negativeConstraints?: string | null,
  ): Promise<string[]> {
    const languageName = languageNameFor(languageCode);
    const constraintsClause =
      negativeConstraints != null && negativeConstraints.trim().length > 0
        ? `CRITICAL INSTRUCTION: Do NOT include any words related to: ${negativeConstraints}.`
        : '';

    const prompt = `Create a list of ${count} vocabulary words related to the topic '${topic}' for an AAC communication board.
Includes nouns, verbs, and adjectives.
${constraintsClause}
Output the words in ${languageName}.
Return ONLY the words, separated by commas.
Example: Dog, Cat, Pet, Walk, Furry, Bark`;

    try {
      const response = await generateTextContent(prompt);
      const text = getResponseText(response);
      if (text == null) return [];
      return text
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
        .slice(0, count);
    } catch (e) {
      console.error('GeminiService: Error in generateBoard', e);
      return [];
    }
  }

  /** Generate additional vocabulary for an existing board, excluding items already present. */
  async generateMoreItems(
    topic: string,
    existingItems: string[],
    count: number,
    languageCode: string = 'en',
  ): Promise<string[]> {
    const languageName = languageNameFor(languageCode);
    const exclusions = existingItems.join(', ');
    const prompt = `Create a list of ${count} NEW vocabulary words related to the topic '${topic}' for an AAC communication board.
Includes nouns, verbs, and adjectives.
The words must be different from: ${exclusions}.
Output the words in ${languageName}.
Return ONLY the words, separated by commas.`;

    try {
      const response = await generateTextContent(prompt);
      const text = getResponseText(response);
      if (text == null) return [];
      return text
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0)
        .slice(0, count);
    } catch (e) {
      console.error('GeminiService: Error in generateMoreItems', e);
      return [];
    }
  }

  /**
   * Convert a GPS coordinate into a generic place category (e.g. "Mall", "School") suitable for
   * an AAC board recommendation.
   *
   * JUDGMENT CALL (per the migration plan's "Native bridge plugins" section, item 2e): the
   * original Kotlin `simplifyLocationName(rawName: String, ...)` took an already reverse-geocoded
   * place name from Android's on-device `Geocoder`. The plan explicitly recommends dropping that
   * Geocoder step and feeding raw lat/lng straight into Gemini in one call instead of adding a
   * REST geocoding dependency, so this signature takes `latitude`/`longitude` instead of
   * `rawName`. The prompt body is adapted accordingly (asking Gemini to infer the place category
   * from the coordinate) — this is the one prompt in this file that isn't a verbatim port, since
   * the original prompt's input shape (a place name string) no longer exists upstream. Gemini
   * has no live grounding/search tool wired up here, so its answer for a bare lat/lng is a
   * best-effort inference from world knowledge, not a real reverse-geocode lookup; this may need
   * revisiting (e.g. Gemini's grounding-with-Google-Search tool) if accuracy proves insufficient
   * in Phase 3/4 testing.
   */
  async simplifyLocationName(
    latitude: number,
    longitude: number,
    languageCode: string = 'en',
  ): Promise<string> {
    const languageName = languageNameFor(languageCode);
    const prompt = `Given this GPS coordinate, infer what kind of place it most likely is and return a generic category suitable for an AAC board recommendation.
Output the category in ${languageName}.
Examples (if English):
- A coordinate at a large retail complex -> 'Mall'
- A coordinate at a K-12 school campus -> 'School'

Coordinate: latitude ${latitude}, longitude ${longitude}

Return ONLY the single category word.`;

    try {
      const response = await generateTextContent(prompt);
      return getResponseText(response) ?? `${latitude}, ${longitude}`;
    } catch (e) {
      console.error('GeminiService: Error in simplifyLocationName', e);
      return `${latitude}, ${longitude}`; // Fallback to raw coordinate
    }
  }

  /** Suggest a short board title (1-3 words) given a handful of representative images. */
  async suggestBoardName(images: GeminiImageInput[], languageCode: string = 'en'): Promise<string> {
    const languageName = languageNameFor(languageCode);
    const prompt = `Analyze these images and provide a single, short, descriptive title for a communication board that handles these items.
The title should be 1-3 words maximum.
Output the title in ${languageName}.
Examples: "Breakfast", "Toys", "Kitchen Items", "My Room".

Return ONLY the title. No punctuation.`;

    // Limit to first 4 images to save bandwidth/token usage if many are selected.
    const imagesToProcess = await Promise.all(images.slice(0, 4).map((img) => scaleImageIfNeeded(img)));

    try {
      const response = await callGemini([...imagesToProcess.map(imagePart), { text: prompt }]);
      return getResponseText(response) ?? '';
    } catch (e) {
      console.error('GeminiService: Error in suggestBoardName', e);
      return '';
    }
  }

  /** Fix the grammar of a sentence, with an optional phrase cache in front of the API call. */
  async correctGrammar(
    sentence: string,
    languageCode: string = 'en',
    cacheService?: PhraseCacheService | null,
  ): Promise<string> {
    // Try cache first if available
    if (cacheService) {
      const cached = await cacheService.getCachedGrammar(sentence, languageCode);
      if (cached != null) {
        return cached;
      }
    }

    const languageName = languageNameFor(languageCode);
    const prompt = `Fix the grammar of this sentence in ${languageName}.
Make it sound natural for spoken conversation.
Return ONLY the corrected sentence. No quotes, no explanations.

Input: "${sentence}"`;

    try {
      const response = await generateTextContent(prompt);
      const corrected = getResponseText(response) ?? sentence;

      // Cache the result if available and different from input
      if (cacheService && corrected !== sentence) {
        await cacheService.cacheGrammar(sentence, corrected, languageCode);
      }

      return corrected;
    } catch (e) {
      console.error('GeminiService: Error in correctGrammar', e);
      return sentence;
    }
  }

  /** Round-trip latency probe (ms), or -1 on failure. */
  async testLatency(prompt: string = 'ping'): Promise<number> {
    const start = Date.now();
    try {
      await generateTextContent(prompt);
      return Date.now() - start;
    } catch (e) {
      console.error('GeminiService: Error in testLatency', e);
      return -1;
    }
  }
}
