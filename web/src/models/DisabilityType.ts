// Mirrors com.example.myaac.data.model.DisabilityType.
//
// Ported as a string-literal union + a const value object rather than a TypeScript
// `enum`: the project's tsconfig has `erasableSyntaxOnly: true` (TS 5.8+/isolatedDeclarations-
// style constraint requiring all syntax to be type-erasable), which rejects real
// `enum` declarations since they emit runtime code. This object+union pattern gives
// the same `DisabilityType.VISUAL_IMPAIRMENT` call-site ergonomics as the Kotlin enum
// while remaining plain erasable TypeScript.
export const DisabilityType = {
  NONE: 'NONE',
  MOTOR_IMPAIRMENT: 'MOTOR_IMPAIRMENT',
  VISUAL_IMPAIRMENT: 'VISUAL_IMPAIRMENT',
  COGNITIVE_IMPAIRMENT: 'COGNITIVE_IMPAIRMENT',
  APHASIA: 'APHASIA',
} as const;

export type DisabilityType = (typeof DisabilityType)[keyof typeof DisabilityType];
