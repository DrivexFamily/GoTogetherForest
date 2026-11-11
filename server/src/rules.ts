// 서버 규칙(상수와 순수 함수). 숲·★ 규칙은 여기 없다: 그것은 차의 core:account가 확정한다(architecture.md 19-1).
import { isB64u } from "./util";

export const MINUTE = 60_000;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

export const LIMITS = {
  /** 숲 상태 암호문 */
  stateBytes: 256 * 1024,
  /** 진행 상태 */
  statusBytes: 8 * 1024,
  /** 한 가족 기기 수(차 포함) */
  devices: 9,
  /** 연결 코드가 살아 있는 시간 */
  pairingMs: 5 * MINUTE,
  /** 연결 코드 틀린 횟수 상한(넘으면 코드 폐기) */
  pairingFails: 5,
  /** 진행 상태 최대 보관(도착 전이라도) */
  statusMaxMs: 24 * HOUR,
  /** 아무 기기도 오지 않으면 가족 기록 삭제 */
  idleMs: 90 * DAY,
  /** 공유 링크 최대 보관(#8) */
  shareMaxMs: 24 * HOUR,
} as const;

/** 폰 권한: 숲 보기·꾸미기·상점 / 우리 차 진행 / 대략 위치 */
export const ROLES = ["forest", "trip", "location"] as const;
export type Role = (typeof ROLES)[number];

export function parseRoles(v: unknown): Role[] | null {
  if (!Array.isArray(v)) return null;
  const out = new Set<Role>();
  for (const r of v) {
    if (!(ROLES as readonly string[]).includes(r)) return null;
    out.add(r as Role);
  }
  // 위치는 진행 보기와 함께만 의미가 있다
  if (out.has("location") && !out.has("trip")) out.add("trip");
  return [...out];
}

/** 암호문 덩어리 {v:1, iv, ct}. 서버는 내용을 모르고 모양만 본다 */
export interface Blob { v: 1; iv: string; ct: string }

export function parseBlob(v: unknown, maxCtChars: number): Blob | null {
  if (typeof v !== "object" || v === null) return null;
  const o = v as Record<string, unknown>;
  if (o.v !== 1 || !isB64u(o.iv, 16, 16) || !isB64u(o.ct, 22, maxCtChars)) return null;
  return { v: 1, iv: o.iv, ct: o.ct };
}

/** 진행 상태 만료 시각: 차가 준 값과 24시간 상한 중 이른 쪽 */
export function statusExpiry(now: number, requested: unknown): number {
  const max = now + LIMITS.statusMaxMs;
  return typeof requested === "number" && requested > now ? Math.min(requested, max) : max;
}

/** 날짜만(일 단위). 마지막 접속은 이 정도로만 남긴다 */
export function dayOf(now: number): string {
  return new Date(now).toISOString().slice(0, 10);
}

/** 인증 헤더: "Device <familyId>.<deviceId>.<deviceKey>" */
export interface DeviceAuth { familyId: string; deviceId: string; key: string }

export function parseDeviceAuth(header: string | null): DeviceAuth | null {
  const m = header?.match(/^Device ([A-Za-z0-9_-]{22})\.([A-Za-z0-9_-]{11})\.([A-Za-z0-9_-]{43})$/);
  return m ? { familyId: m[1]!, deviceId: m[2]!, key: m[3]! } : null;
}

/** 공유 링크 쓰기 키: "Share <writeKey>" */
export function parseShareAuth(header: string | null): string | null {
  const m = header?.match(/^Share ([A-Za-z0-9_-]{43})$/);
  return m ? m[1]! : null;
}

/** 공유 링크 값(#8): 진행률·남은 시간·단계·선택적 5km 격자. 좌표 정밀도는 기기에서 이미 줄였다 */
export interface SharePayload { progress: number; remainingMin: number | null; phase: string; grid?: { lat: number; lon: number } }

const PHASES = ["driving", "resting", "arrived"];

export function parseSharePayload(o: Record<string, unknown>): SharePayload | null {
  const { progress, remainingMin, phase, grid } = o;
  if (typeof progress !== "number" || !(progress >= 0 && progress <= 100)) return null;
  if (remainingMin !== null && (typeof remainingMin !== "number" || remainingMin < 0 || remainingMin > 24 * 60)) return null;
  if (typeof phase !== "string" || !PHASES.includes(phase)) return null;
  const out: SharePayload = { progress: Math.round(progress), remainingMin: remainingMin === null ? null : Math.round(remainingMin), phase };
  if (grid !== undefined) {
    const g = grid as Record<string, unknown> | null;
    if (!g || typeof g.lat !== "number" || typeof g.lon !== "number" || Math.abs(g.lat) > 90 || Math.abs(g.lon) > 180) return null;
    // 서버도 한 번 더 0.05도(약 5km) 칸으로 맞춘다: 기기가 실수로 정밀한 값을 보내도 저장하지 않는다
    out.grid = { lat: Math.round(g.lat * 20) / 20, lon: Math.round(g.lon * 20) / 20 };
  }
  return out;
}

/** 짧은 연결 코드: 헷갈리는 글자(0·1·O·I) 없는 32글자 중 12글자(약 60비트). 사람이 칠 때 "-"·빈칸은 빼고 받는다 */
export const SHORT_CODE = /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{12}$/;
