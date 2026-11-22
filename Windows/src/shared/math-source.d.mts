export type MathSegment = { kind: 'text'; text: string } | { kind: 'math'; text: string; source: string; display: boolean };
export function mathSegments(source: string): MathSegment[];
export function mathSearchText(source: string): string;
