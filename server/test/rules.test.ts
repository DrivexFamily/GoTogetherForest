import { describe, expect, it } from "vitest";
import { parseDeviceAuth, parseRoles, parseSharePayload, statusExpiry, LIMITS } from "../src/rules";

describe("rules", () => {
  it("기기 인증 헤더는 정해진 길이만 받는다", () => {
    const f = "A".repeat(22), d = "B".repeat(11), k = "C".repeat(43);
    expect(parseDeviceAuth(`Device ${f}.${d}.${k}`)).toEqual({ familyId: f, deviceId: d, key: k });
    expect(parseDeviceAuth(`Bearer ${k}`)).toBeNull();
    expect(parseDeviceAuth(`Device ${f}.${d}.${k}x`)).toBeNull();
  });

  it("위치 권한은 진행 보기를 함께 켠다, 모르는 권한은 거절", () => {
    expect(parseRoles(["location"])?.sort()).toEqual(["location", "trip"]);
    expect(parseRoles(["admin"])).toBeNull();
  });

  it("진행 상태는 24시간을 넘기지 않는다", () => {
    expect(statusExpiry(0, 48 * 3600_000)).toBe(LIMITS.statusMaxMs);
    expect(statusExpiry(0, 3600_000)).toBe(3600_000);
  });

  it("공유 격자는 서버에서도 약 5km(0.05도)로 맞춘다", () => {
    const p = parseSharePayload({ progress: 40.4, remainingMin: 80, phase: "driving", grid: { lat: 37.56661, lon: 126.97839 } });
    expect(p?.grid).toEqual({ lat: 37.55, lon: 127 });
    expect(p?.progress).toBe(40);
    expect(parseSharePayload({ progress: 140, remainingMin: 1, phase: "driving" })).toBeNull();
  });
});
